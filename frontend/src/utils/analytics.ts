type AnalyticsValue = string | number | boolean | null | undefined;
type AnalyticsParams = Record<string, AnalyticsValue>;

declare global {
  interface Window {
    dataLayer?: IArguments[];
    gtag?: (...args: unknown[]) => void;
  }
}

const MEASUREMENT_ID = (import.meta.env.VITE_GA_MEASUREMENT_ID ?? '').trim();
let initialized = false;
let lastTrackedPath = '';

function hasValidMeasurementId() {
  return /^G-[A-Z0-9]+$/i.test(MEASUREMENT_ID);
}

function currentPath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

function safeGtag(...args: unknown[]) {
  if (typeof window === 'undefined' || !window.gtag || !hasValidMeasurementId()) return;
  window.gtag(...args);
}

export function initAnalytics() {
  if (typeof window === 'undefined' || initialized || !hasValidMeasurementId()) return;
  initialized = true;

  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag() {
    window.dataLayer?.push(arguments);
  };

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(MEASUREMENT_ID)}`;
  document.head.appendChild(script);

  safeGtag('js', new Date());
  // Disable the automatic first page_view so we can send consistent SPA page views ourselves.
  safeGtag('config', MEASUREMENT_ID, { send_page_view: false });
  trackPageView();

  const notifyRouteChange = () => window.setTimeout(() => trackPageView(), 0);
  const wrapHistoryMethod = (methodName: 'pushState' | 'replaceState') => {
    const original = window.history[methodName];
    window.history[methodName] = function patchedHistoryMethod(...args) {
      const result = original.apply(this, args);
      notifyRouteChange();
      return result;
    };
  };

  wrapHistoryMethod('pushState');
  wrapHistoryMethod('replaceState');
  window.addEventListener('popstate', notifyRouteChange);
}

export function trackPageView(path = currentPath()) {
  if (!hasValidMeasurementId() || path === lastTrackedPath) return;
  lastTrackedPath = path;
  safeGtag('event', 'page_view', {
    page_title: document.title,
    page_location: window.location.href,
    page_path: path
  });
}

export function trackEvent(name: string, params: AnalyticsParams = {}) {
  if (!hasValidMeasurementId()) return;
  safeGtag('event', name, params);
}
