import { createPortal } from 'react-dom';
import { formatCurrency } from '../utils/planner';
import { useLocale } from '../LocaleContext';

// Sprint 10.168: a clean, print-only shopping list (Print → "Save as PDF"). Rendered via a portal to <body>
// so the print stylesheet can isolate it (body > *:not(.printable-plan){display:none}) and drop all the app
// chrome. Shared by the single-room results and the whole-apartment ("Cijeli stan") view so both PDFs match.
export interface PrintSection {
  title: string;
  items: Array<{ name: string; meta: string; lineTotal: number }>;
  subtotal: number;
}

interface PrintablePlanProps {
  title: string;
  subtitle?: string;
  budget: number;
  total: number;
  sections: PrintSection[];
  stores: Array<{ retailer: string; count: number; total: number }>;
  market?: string;
}

export function PrintablePlan({ title, subtitle, budget, total, sections, stores, market }: PrintablePlanProps) {
  const { t } = useLocale();
  if (typeof document === 'undefined') return null;
  const remaining = budget - total;
  return createPortal(
    <div className="printable-plan" aria-hidden="true">
      <div className="print-head">
        <span className="print-brand">budgetspace</span>
        <span className="print-kicker">{t('print.shoppingList')}</span>
      </div>
      <h1 className="print-title">{title}</h1>
      {subtitle && <div className="print-subtitle">{subtitle}</div>}

      <div className="print-summary">
        <span>{t('print.budget')}: <strong>{formatCurrency(budget, market)}</strong></span>
        <span>{t('print.total')}: <strong>{formatCurrency(total, market)}</strong></span>
        <span>{remaining >= 0 ? t('print.remaining') : t('print.over')}: <strong>{formatCurrency(Math.abs(remaining), market)}</strong></span>
      </div>

      {sections.map((section, sectionIndex) => (
        <div className="print-section" key={sectionIndex}>
          <div className="print-section-head">
            <span>{section.title}</span>
            <strong>{formatCurrency(section.subtotal, market)}</strong>
          </div>
          <table className="print-table">
            <tbody>
              {section.items.map((item, itemIndex) => (
                <tr key={itemIndex}>
                  <td className="print-item-name">{item.name}</td>
                  <td className="print-item-meta">{item.meta}</td>
                  <td className="print-item-price">{formatCurrency(item.lineTotal, market)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}

      {stores.length > 0 && (
        <div className="print-section print-stores">
          <div className="print-section-head"><span>{t('print.byStore')}</span></div>
          <table className="print-table">
            <tbody>
              {stores.map((store, storeIndex) => (
                <tr key={storeIndex}>
                  <td className="print-item-name">{store.retailer}</td>
                  <td className="print-item-meta">{t('moveIn.itemsCount', { count: store.count })}</td>
                  <td className="print-item-price">{formatCurrency(store.total, market)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="print-foot">{t('print.disclaimer')} · {t('print.madeWith')}</div>
    </div>,
    document.body,
  );
}
