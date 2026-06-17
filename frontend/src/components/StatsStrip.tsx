import { useLocale } from '../LocaleContext';

export function StatsStrip() {
  const { t } = useLocale();
  const stats = [
    { value: '3', label: t('stats.suggestions') },
    { value: '5+', label: t('stats.stores') },
    { value: '€', label: t('stats.budgetVisible') },
    { value: 'Link', label: t('stats.shareable') }
  ];

  return (
    <section className="stats shell" aria-label="Product highlights">
      {stats.map((stat) => (
        <div className="stat" key={stat.label}>
          <strong>{stat.value}</strong>
          <span>{stat.label}</span>
        </div>
      ))}
    </section>
  );
}
