import { Fragment } from 'react';
import { useLocale } from '../LocaleContext';

// Sprint 10.182: "Kako radi" reworked (owner mockup) — a heading block beside a horizontal 3-step flow. Each step
// is a numbered, COLOUR-TINTED icon (clay → sage → navy) with a title + one line, joined by copper arrows. The
// three tints add warmth/variety so the section no longer reads monotone. Stacks (icon left, text right) on phones.
export function HowItWorks() {
  const { t } = useLocale();

  const steps = [
    {
      num: '01',
      title: t('how.step1Title'),
      text: t('how.step1Text'),
      icon: ( // describe — a pencil
        <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
          <path d="M4 20h4L18.5 9.5a1.8 1.8 0 0 0 0-2.5l-1.5-1.5a1.8 1.8 0 0 0-2.5 0L4 16v4z" />
          <path d="M13.5 6.5l4 4" />
        </svg>
      )
    },
    {
      num: '02',
      title: t('how.step2Title'),
      text: t('how.step2Text'),
      icon: ( // concrete products — a sofa
        <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
          <path d="M5 11V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3" />
          <rect x="3" y="11" width="18" height="6" rx="2" />
          <path d="M6 17v2M18 17v2" />
        </svg>
      )
    },
    {
      num: '03',
      title: t('how.step3Title'),
      text: t('how.step3Text'),
      icon: ( // shopping list — a clipboard
        <svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
          <rect x="5" y="4.5" width="14" height="16.5" rx="2" />
          <path d="M9 4.5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 4.5V6H9z" />
          <path d="M9 11h6M9 15h4" />
        </svg>
      )
    }
  ];

  return (
    <section className="section shell how" id="how">
      <div className="how-inner">
        <div className="how-heading">
          <span className="eyebrow">{t('how.eyebrow')}</span>
          <h2>{t('how.heading')}</h2>
          <p>{t('how.subheading')}</p>
        </div>
        <div className="how-steps">
          {steps.map((step, i) => (
            <Fragment key={step.num}>
              {i > 0 && <span className="how-arrow" aria-hidden="true">→</span>}
              <div className={`how-step how-tint-${i + 1}`}>
                <span className="how-step-num">{step.num}</span>
                <span className="how-step-icon" aria-hidden="true">{step.icon}</span>
                <div className="how-step-body">
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                </div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>
    </section>
  );
}
