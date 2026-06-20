// Sprint 10.72 — renders a legal document (privacy / terms / impressum) in a modal overlay. Content lives in
// legal.ts (HR + EN, English fallback for other locales). The app has no router, so legal pages are modals
// opened from the footer.
import { useEffect } from 'react';
import { useLocale } from '../LocaleContext';
import { legalDoc, type LegalKey } from '../legal';

export function LegalModal({ docKey, onClose }: { docKey: LegalKey | null; onClose: () => void }) {
  const { config, t } = useLocale();

  useEffect(() => {
    if (!docKey) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [docKey, onClose]);

  if (!docKey) return null;
  const doc = legalDoc(config.lang, docKey);

  return (
    <div className="legal-overlay" role="dialog" aria-modal="true" aria-label={doc.title} onClick={onClose}>
      <div className="legal-modal" onClick={(event) => event.stopPropagation()}>
        <div className="legal-modal-head">
          <h2>{doc.title}</h2>
          <button type="button" className="legal-close" aria-label={t('legal.close')} onClick={onClose}>×</button>
        </div>
        <p className="legal-updated">{doc.updated}</p>
        {doc.disclaimer && <p className="legal-disclaimer">{doc.disclaimer}</p>}
        <div className="legal-body">
          {doc.sections.map((section, i) => (
            <section key={i}>
              <h3>{section.heading}</h3>
              {section.body.map((paragraph, j) => <p key={j}>{paragraph}</p>)}
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}
