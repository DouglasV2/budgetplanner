import { useLocale } from '../LocaleContext';

export function HowItWorks() {
  const { t } = useLocale();
  const steps = [
    { title: t('how.step1Title'), text: t('how.step1Text') },
    { title: t('how.step2Title'), text: t('how.step2Text') },
    { title: t('how.step3Title'), text: t('how.step3Text') }
  ];

  return (
    <section className="section shell" id="how">
      <div className="section-heading">
        <span className="eyebrow">{t('how.eyebrow')}</span>
        <h2>{t('how.heading')}</h2>
        <p>{t('how.subheading')}</p>
      </div>
      <div className="steps-grid">
        {steps.map((step) => (
          <article className="step-card" key={step.title}>
            <h3>{step.title}</h3>
            <p>{step.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
