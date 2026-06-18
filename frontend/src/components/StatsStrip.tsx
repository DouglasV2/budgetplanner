import { useLocale } from '../LocaleContext';

export function StatsStrip() {
  const { t, config } = useLocale();
  // Sprint 10.46: show the active market's currency symbol (€, kr, …), not a hardcoded euro.
  const currencySymbol =
    new Intl.NumberFormat(config.locale, { style: 'currency', currency: config.currency, maximumFractionDigits: 0 })
      .formatToParts(0)
      .find((part) => part.type === 'currency')?.value ?? '€';
  const stats = [
    { value: '3', label: t('stats.suggestions') },
    { value: '5+', label: t('stats.stores') },
    { value: currencySymbol, label: t('stats.budgetVisible') },
    { value: 'Link', label: t('stats.shareable') }
  ];

  return (
    <section className="stats shell" aria-label={t('aria.productHighlights')}>
      {stats.map((stat) => (
        <div className="stat" key={stat.label}>
          <strong>{stat.value}</strong>
          <span>{stat.label}</span>
        </div>
      ))}
    </section>
  );
}
