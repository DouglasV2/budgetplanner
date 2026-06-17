import { useLocale } from '../LocaleContext';

export function Footer() {
  const { t } = useLocale();
  return (
    <footer className="footer shell">
      <div>
        <strong>BudgetSpace</strong>
        <p>{t('footer.tagline')}</p>
      </div>
      <a href="#top">{t('footer.backToTop')}</a>
    </footer>
  );
}
