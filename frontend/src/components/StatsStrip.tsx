export function StatsStrip() {
  const stats = [
    { value: '3', label: 'prijedloga po upitu' },
    { value: '5+', label: 'trgovina u katalogu' },
    { value: '€', label: 'budžet je uvijek vidljiv' },
    { value: 'Link', label: 'plan se može podijeliti' }
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
