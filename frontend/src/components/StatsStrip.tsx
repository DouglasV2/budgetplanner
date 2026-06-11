export function StatsStrip() {
  const stats = [
    { value: '3', label: 'plana po upitu' },
    { value: '5+', label: 'retailera u katalogu' },
    { value: '€', label: 'budget-first logika' },
    { value: 'SEO', label: 'web-first strategija' }
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
