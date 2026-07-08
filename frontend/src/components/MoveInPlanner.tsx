import { useEffect, useState } from 'react';
import type { FurnishingPlan, PlannerInput, Product, RoomType, SavedPlanResponse } from '../types';
import { generateMoveInPlan, replaceProduct, savePlan, trackProductClick } from '../api/client';
import { formatCurrency } from '../utils/planner';
import { useLocale } from '../LocaleContext';
import { RoomIcon, CategoryIcon, SwapIcon, CloseIcon } from './icons';
import { openPlanPdf } from '../utils/planPdf';

// Sprint 10.137: open each item on the retailer's product page (same as the single-room plan). A whole-apartment
// plan that lists prices but can't be clicked through to the store isn't actually shoppable. Mirrors PlanResults.
function storeUrl(product: Product): string {
  const url = product.productUrl || product.url || '';
  return url.startsWith('http') ? url : '';
}

// Sprint 10.162: the REAL retailer photo only when it was verified on the live product page (same honesty gate
// as PlanResults). Otherwise null → the row shows the crafted category icon, never a misleading stock photo.
function productPhoto(product: Product): string | null {
  return product.imageVerified && product.imageUrl ? product.imageUrl : null;
}

const qtyOf = (item: { quantity?: number }) => (item.quantity && item.quantity > 1 ? item.quantity : 1);

// Sprint 10.138: localStorage draft so the room picks + budget survive a reload (only numbers/room ids — no PII).
const MOVE_IN_DRAFT_KEY = 'budgetspace.moveInDraft';

// Sprint 10.138: "what to buy where" — roll every item across all rooms up by retailer (count + total), so a
// multi-room shop is actually plannable ("at IKEA grab 9 pieces for 1.240 €").
function aggregateByStore(results: RoomPlanResult[]): Array<{ retailer: string; count: number; total: number }> {
  const byStore = new Map<string, { count: number; total: number }>();
  for (const result of results) {
    for (const item of result.plan.items) {
      const qty = qtyOf(item);
      const current = byStore.get(item.product.retailer) ?? { count: 0, total: 0 };
      current.count += qty;
      current.total += item.product.price * qty;
      byStore.set(item.product.retailer, current);
    }
  }
  return [...byStore.entries()]
    .map(([retailer, value]) => ({ retailer, ...value }))
    .sort((a, b) => b.total - a.total);
}

// Sprint 10.109: Move-In ("Cijeli stan") — the apartment branch of the planner. Self-contained; the single-room
// flow in Planner.tsx is untouched. Phase 2 (10.110): the budget split is now CATALOG-FLOOR-AWARE and done on
// the backend (POST /api/plans/generate-move-in) — each room first reserves its cheapest core pieces, and an
// honest "budget too low" signal fires when the total can't cover every room's core.

interface MoveInPlannerProps {
  // The current single-room form input — we inherit its shared settings (style, stores, market, location, size).
  baseInput: PlannerInput;
  // The active "space" (home) that saved rooms group under, e.g. "Moj dom".
  activeSpace: string;
  onSavedPlan: (plan: SavedPlanResponse) => void;
  onNotice: (message: string) => void;
  // Sprint 10.116: when the user is nudged here from a multi-room free-text prompt, pre-fill the rooms it found
  // + the budget they typed. A fresh object each nudge so the effect re-applies.
  seed?: { rooms: RoomType[]; budget: number } | null;
}

const MOVE_IN_ROOMS: Array<{ value: RoomType; labelKey: string }> = [
  { value: 'living-room', labelKey: 'form.roomLivingRoomLabel' },
  { value: 'bedroom', labelKey: 'form.roomBedroomLabel' },
  { value: 'home-office', labelKey: 'form.roomHomeOfficeLabel' },
  { value: 'dining-room', labelKey: 'form.roomDiningRoomLabel' },
  { value: 'kitchen', labelKey: 'form.roomKitchenLabel' },
  { value: 'hallway', labelKey: 'form.roomHallwayLabel' },
  { value: 'bathroom', labelKey: 'form.roomBathroomLabel' }
];

interface RoomPlanResult {
  roomType: RoomType;
  labelKey: string;
  allocatedBudget: number;
  plan: FurnishingPlan;
  input: PlannerInput;
  hasItems: boolean;
  partial: boolean;
}

// The 3 tiers come back with stable ids budget/value/stretch (plan.name is a Croatian display string).
// Sprint 10.155: match on the stable id, not the HR name, so a reworded plan.name can't silently break the
// default pick. For the apartment overview we default to the balanced "value" tier per room.
function pickBestPlan(plans: FurnishingPlan[]): FurnishingPlan | null {
  if (!plans.length) return null;
  return plans.find((plan) => plan.id === 'value') ?? plans[Math.min(1, plans.length - 1)] ?? plans[0];
}

export function MoveInPlanner({ baseInput, activeSpace, onSavedPlan, onNotice, seed }: MoveInPlannerProps) {
  const { t } = useLocale();
  // Sprint 10.138: hydrate the room picks + budget from the last session (localStorage draft) so a reload or a
  // return visit doesn't reset the form. Falls back to sensible defaults.
  const [selectedRooms, setSelectedRooms] = useState<RoomType[]>(() => {
    try {
      const draft = JSON.parse(localStorage.getItem(MOVE_IN_DRAFT_KEY) || 'null');
      if (draft && Array.isArray(draft.rooms) && draft.rooms.length) return draft.rooms as RoomType[];
    } catch { /* ignore */ }
    return ['living-room', 'bedroom'];
  });
  const [totalBudget, setTotalBudget] = useState<number>(() => {
    try {
      const draft = JSON.parse(localStorage.getItem(MOVE_IN_DRAFT_KEY) || 'null');
      if (draft && typeof draft.budget === 'number' && draft.budget > 0) return draft.budget;
    } catch { /* ignore */ }
    return 5000;
  });
  const [swapping, setSwapping] = useState<string | null>(null);
  const [results, setResults] = useState<RoomPlanResult[] | null>(null);
  const [apartmentPartial, setApartmentPartial] = useState(false);
  const [shortfall, setShortfall] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Sprint 10.116: apply a seed (from the multi-room nudge) — pre-select the detected rooms + typed budget.
  useEffect(() => {
    if (seed && seed.rooms.length) {
      setSelectedRooms(seed.rooms);
      if (seed.budget > 0) setTotalBudget(seed.budget);
    }
  }, [seed]);

  // Sprint 10.138: persist the room picks + budget (draft) so the next visit starts where the user left off.
  useEffect(() => {
    try {
      localStorage.setItem(MOVE_IN_DRAFT_KEY, JSON.stringify({ rooms: selectedRooms, budget: totalBudget }));
    } catch { /* ignore quota/private-mode */ }
  }, [selectedRooms, totalBudget]);

  function toggleRoom(room: RoomType) {
    setSelectedRooms((current) => (current.includes(room) ? current.filter((value) => value !== room) : [...current, room]));
  }

  function buildRoomInput(roomType: RoomType, budget: number): PlannerInput {
    const roomName = t(MOVE_IN_ROOMS.find((room) => room.value === roomType)?.labelKey ?? '');
    return {
      ...baseInput,
      roomType,
      budget,
      prompt: `${roomName} — ${formatCurrency(budget)}`,
      mustHaveCategories: [],
      alreadyHaveCategories: [],
      lockedProductIds: [],
      market: baseInput.market
    };
  }

  async function runMoveIn() {
    if (!selectedRooms.length) {
      setError(t('moveIn.needRooms'));
      return;
    }
    setError(null);
    setIsLoading(true);
    setResults(null);
    setApartmentPartial(false);
    setShortfall(0);
    try {
      // Keep a stable top-down order (by the picker list), independent of click order.
      const orderedRooms = MOVE_IN_ROOMS.filter((room) => selectedRooms.includes(room.value)).map((room) => room.value);
      const response = await generateMoveInPlan(baseInput, orderedRooms, totalBudget);

      const mapped: RoomPlanResult[] = response.rooms
        .map((room) => {
          const plan = pickBestPlan(room.plans);
          if (!plan) return null;
          return {
            roomType: room.roomType,
            labelKey: MOVE_IN_ROOMS.find((entry) => entry.value === room.roomType)?.labelKey ?? '',
            allocatedBudget: room.allocatedBudget,
            plan,
            input: buildRoomInput(room.roomType, room.allocatedBudget),
            hasItems: plan.items.length > 0,
            partial: room.partial
          } as RoomPlanResult;
        })
        .filter((result): result is RoomPlanResult => result !== null);

      if (!mapped.length) {
        setError(t('moveIn.error'));
        return;
      }
      setResults(mapped);
      setApartmentPartial(response.apartmentPartial);
      setShortfall(response.shortfall);
    } catch {
      setError(t('moveIn.error'));
    } finally {
      setIsLoading(false);
    }
  }

  async function saveApartment() {
    if (!results) return;
    setSaving(true);
    let savedCount = 0;
    try {
      for (const result of results) {
        if (!result.hasItems) continue;
        try {
          const saved = await savePlan(result.plan, result.input, activeSpace);
          onSavedPlan(saved);
          savedCount += 1;
        } catch {
          // A single room failing to save (e.g. a cap) shouldn't abort the rest.
        }
      }
      onNotice(t('moveIn.savedApartment', { count: savedCount, name: activeSpace }));
    } finally {
      setSaving(false);
    }
  }

  // Sprint 10.138: let the user drop a piece they don't want — recompute the room (and apartment) total locally.
  function removeItem(roomType: RoomType, productId: string) {
    setResults((prev) => prev && prev.map((result) => {
      if (result.roomType !== roomType) return result;
      const items = result.plan.items.filter((item) => item.product.id !== productId);
      return {
        ...result,
        hasItems: items.length > 0,
        plan: {
          ...result.plan,
          items,
          total: items.reduce((sum, item) => sum + item.product.price * qtyOf(item), 0),
          retailersUsed: [...new Set(items.map((item) => item.product.retailer))]
        }
      };
    }));
  }

  // Sprint 10.138: swap a piece for a similar one (same /api/plans/replace the single-room plan uses; it returns
  // the whole updated room plan, so we just splice it back in).
  async function swapItem(roomType: RoomType, productId: string) {
    const room = results?.find((result) => result.roomType === roomType);
    if (!room) return;
    setSwapping(productId);
    try {
      const updated = await replaceProduct(room.plan, room.input, productId, 'similar');
      setResults((prev) => prev && prev.map((result) => (result.roomType === roomType ? { ...result, plan: updated, hasItems: updated.items.length > 0 } : result)));
    } catch {
      onNotice(t('moveIn.error'));
    } finally {
      setSwapping(null);
    }
  }

  // Sprint 10.138: a plain-text shopping list (per room: pieces + prices + store links) the user can paste into
  // Notes / a message / email and actually take shopping. Mirrors the single-room share text.
  function buildApartmentList(list: RoomPlanResult[]): string {
    const grand = list.reduce((sum, result) => sum + result.plan.total, 0);
    const lines = [`${t('moveIn.grandTotalLabel')}: ${formatCurrency(grand)} ${t('moveIn.ofBudget', { budget: formatCurrency(totalBudget) })}`];
    for (const result of list) {
      if (!result.hasItems) continue;
      lines.push('', `${t(result.labelKey)} — ${formatCurrency(result.plan.total)}`);
      for (const item of result.plan.items) {
        const qty = qtyOf(item);
        const name = qty > 1 ? `${qty} × ${item.product.name}` : item.product.name;
        const url = storeUrl(item.product);
        lines.push(`- ${name} — ${formatCurrency(item.product.price * qty)}${url ? ` ${url}` : ''}`);
      }
    }
    lines.push('', t('results.shareFooter'));
    return lines.join('\n');
  }

  async function copyList() {
    if (!results) return;
    try {
      await navigator.clipboard.writeText(buildApartmentList(results));
      onNotice(t('results.listCopied'));
    } catch { /* clipboard blocked (e.g. insecure context) — no-op */ }
  }

  const total = results ? results.reduce((sum, result) => sum + result.plan.total, 0) : 0;
  const over = total - totalBudget;
  const storeRollup = results ? aggregateByStore(results) : [];
  // Sprint 10.168: a clean whole-apartment shopping-list PDF — grouped by room, matching the single-room PDF.
  const printSections = (results ?? []).filter((result) => result.hasItems).map((result) => ({
    title: t(result.labelKey),
    subtotal: result.plan.total,
    items: result.plan.items.map((item) => {
      const qty = qtyOf(item);
      return {
        name: qty > 1 ? `${qty} × ${item.product.name}` : item.product.name,
        meta: item.product.retailer,
        lineTotal: item.product.price * qty,
      };
    }),
  }));

  return (
    <div className="move-in">
      <div className="form-step move-in-form">
        <h3>{t('moveIn.heading')}</h3>
        <p>{t('moveIn.intro')}</p>

        <div className="control-block budget-block">
          <span className="friendly-label">{t('moveIn.totalBudgetLabel')}</span>
          <label className="budget-input-wrap">
            <input
              aria-label={t('moveIn.totalBudgetLabel')}
              type="number"
              inputMode="numeric"
              min="200"
              step="100"
              // Sprint: show empty (not a stuck "0") when cleared — value={number} forced a leading zero you
              // couldn't delete (clear 5000 -> 0 -> "0", then typing gave "07000"). Mirrors the single-room field.
              value={totalBudget || ''}
              onChange={(event) => setTotalBudget(Math.min(10_000_000, Math.max(0, Math.floor(Number(event.target.value) || 0))))}
            />
            <span>€</span>
          </label>
        </div>

        <div className="control-block">
          <div className="move-in-rooms-head">
            <span className="friendly-label">{t('moveIn.whichRoomsLabel')} <small>({t('moveIn.whichRoomsHint')})</small></span>
            <small className="move-in-count">{t('moveIn.roomsSelected', { count: selectedRooms.length })}</small>
          </div>
          <div className="choice-grid rooms friendly-rooms">
            {MOVE_IN_ROOMS.map((room) => (
              <button
                type="button"
                key={room.value}
                className={selectedRooms.includes(room.value) ? 'choice active' : 'choice'}
                aria-pressed={selectedRooms.includes(room.value)}
                onClick={() => toggleRoom(room.value)}
              >
                <span className="choice-icon"><RoomIcon room={room.value} /></span>
                <strong>{t(room.labelKey)}</strong>
              </button>
            ))}
          </div>
        </div>

        <button type="button" className="generate-button" disabled={isLoading} onClick={() => void runMoveIn()}>
          {isLoading ? t('moveIn.generating') : t('moveIn.generate')}
        </button>
        {error && <p className="planner-notice" role="alert">{error}</p>}
        {!results && !error && <p className="move-in-empty-hint">{t('moveIn.emptyHint')}</p>}
      </div>

      {results && (
        <div className="move-in-results">
          {apartmentPartial && (
            <p className="move-in-budget-low" role="status">{t('moveIn.budgetLow', { amount: formatCurrency(shortfall) })}</p>
          )}
          <div className="move-in-total-card">
            <div className="move-in-total-row">
              <div className="move-in-total-headline">
                <span className="move-in-total-label">{t('moveIn.grandTotalLabel')}</span>
                <div className="move-in-total-figure">
                  <strong className="move-in-total-value">{formatCurrency(total)}</strong>
                  <span className="move-in-total-budget">{t('moveIn.ofBudget', { budget: formatCurrency(totalBudget) })}</span>
                </div>
              </div>
              <div className="move-in-total-statuscol">
                <span className={over > 0 ? 'move-in-status over' : 'move-in-status within'}>
                  {over > 0 ? t('moveIn.overBudget', { amount: formatCurrency(over) }) : t('moveIn.withinBudget')}
                </span>
                {over < 0 && <span className="move-in-total-left">{t('moveIn.budgetLeft', { amount: formatCurrency(-over) })}</span>}
              </div>
            </div>
            {/* Sprint 10.162: the "budget filling a space" motif, applied to the whole apartment. */}
            <div className="move-in-fillbar move-in-fillbar-lg" aria-hidden="true">
              <span className={over > 0 ? 'over' : ''} style={{ width: `${Math.min(100, Math.round((total / Math.max(1, totalBudget)) * 100))}%` }} />
            </div>
          </div>

          {/* Sprint 10.138: take the list with you — copy as text (paste into Notes / a message) or print it. */}
          <div className="move-in-actions no-print">
            <button type="button" className="move-in-action-btn" onClick={() => void copyList()}>{t('results.copyShoppingList')}</button>
            <button type="button" className="move-in-action-btn" onClick={() => void openPlanPdf({
              title: t('moveIn.heading'),
              subtitle: t('moveIn.roomsSelected', { count: results.length }),
              budget: totalBudget,
              total,
              sections: printSections,
              stores: storeRollup,
              money: (value) => formatCurrency(value, baseInput.market),
              labels: {
                shoppingList: t('print.shoppingList'), budget: t('print.budget'), total: t('print.total'),
                remaining: t('print.remaining'), over: t('print.over'), byStore: t('print.byStore'),
                disclaimer: t('print.disclaimer'), madeWith: t('print.madeWith'),
                itemsCount: (count) => t('moveIn.itemsCount', { count }),
              },
            })}>{t('print.downloadPdf')}</button>
          </div>

          <div className="move-in-room-cards">
            {results.map((result) => (
              <article className="move-in-room-card" key={result.roomType}>
                <div className="move-in-room-head">
                  <span className="move-in-room-title">
                    <span className="move-in-room-roomicon" aria-hidden="true"><RoomIcon room={result.roomType} size={17} /></span>
                    <strong>{t(result.labelKey)}</strong>
                  </span>
                  {result.hasItems && (
                    <span className="move-in-room-count">{t('moveIn.itemsCount', { count: result.plan.items.length })}</span>
                  )}
                </div>
                {result.hasItems ? (
                  <>
                    <div className="move-in-room-spend">
                      <span className="move-in-room-total">{formatCurrency(result.plan.total)}</span>
                      <span className="move-in-room-budget">{t('moveIn.roomBudget', { amount: formatCurrency(result.allocatedBudget) })}</span>
                    </div>
                    {/* Per-room "budget filling a space" bar — how full this room is against its slice. */}
                    <div className="move-in-fillbar" aria-hidden="true">
                      <span style={{ width: `${Math.min(100, Math.round((Number(result.plan.total) / Math.max(1, result.allocatedBudget)) * 100))}%` }} />
                    </div>
                    {result.plan.retailersUsed.length > 0 && (
                      <div className="move-in-room-stores">
                        {result.plan.retailersUsed.map((retailer) => (
                          <span key={retailer} className="move-in-store-chip">{retailer}</span>
                        ))}
                      </div>
                    )}
                    {/* Sprint 10.129: show the FULL per-room list. Sprint 10.162: each item now leads with a
                        thumbnail — the real verified photo, or the crafted category icon when unverified. */}
                    <ul className="move-in-room-items">
                      {result.plan.items.map((item) => {
                        const qty = qtyOf(item);
                        const name = qty > 1 ? `${qty} × ${item.product.name}` : item.product.name;
                        const lineTotal = item.product.price * qty;
                        const openUrl = storeUrl(item.product);
                        const photo = productPhoto(item.product);
                        const thumb = (
                          <span className="move-in-thumb" aria-hidden="true">
                            {photo
                              ? <img src={photo} alt="" loading="lazy" referrerPolicy="no-referrer" />
                              : <CategoryIcon category={item.product.category} size={24} />}
                          </span>
                        );
                        return (
                          <li key={item.product.id} className="move-in-item">
                            {openUrl ? (
                              <a
                                className="move-in-item-row move-in-item-link"
                                href={openUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                title={t('results.openInStore')}
                                onClick={() => trackProductClick(result.plan.id, item.product)}
                              >
                                {thumb}
                                <span className="move-in-item-name">{name}</span>
                                <span className="move-in-item-price">{formatCurrency(lineTotal)}</span>
                              </a>
                            ) : (
                              <div className="move-in-item-row">
                                {thumb}
                                <span className="move-in-item-name">{name}</span>
                                <span className="move-in-item-price">{formatCurrency(lineTotal)}</span>
                              </div>
                            )}
                            {/* Sprint 10.138: swap for a similar piece / drop it — not buttons inside the <a> (invalid). */}
                            <span className="move-in-item-actions no-print">
                              <button
                                type="button"
                                className="move-in-item-act"
                                title={t('moveIn.swapItem')}
                                aria-label={t('moveIn.swapItem')}
                                disabled={swapping === item.product.id}
                                onClick={() => void swapItem(result.roomType, item.product.id)}
                              >
                                {swapping === item.product.id ? '…' : <SwapIcon />}
                              </button>
                              <button
                                type="button"
                                className="move-in-item-act move-in-item-remove"
                                title={t('moveIn.removeItem')}
                                aria-label={t('moveIn.removeItem')}
                                onClick={() => removeItem(result.roomType, item.product.id)}
                              >
                                <CloseIcon />
                              </button>
                            </span>
                          </li>
                        );
                      })}
                    </ul>
                    {result.partial && <small className="move-in-room-partial">{t('moveIn.partialRoom')}</small>}
                  </>
                ) : (
                  <p className="move-in-room-empty">{t('moveIn.noProducts')}</p>
                )}
              </article>
            ))}
          </div>

          {/* Sprint 10.138: "what to buy where" — every piece rolled up by store so a multi-room shop is plannable. */}
          {storeRollup.length > 0 && (
            <div className="move-in-by-store">
              <span className="move-in-by-store-title">{t('moveIn.byStore')}</span>
              <ul>
                {storeRollup.map((store) => (
                  <li key={store.retailer}>
                    <span className="move-in-store-name">{store.retailer}</span>
                    <span className="move-in-store-meta">{t('moveIn.itemsCount', { count: store.count })} · {formatCurrency(store.total)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <button type="button" className="generate-button move-in-save no-print" disabled={saving} onClick={() => void saveApartment()}>
            {t('moveIn.saveApartment')}
          </button>
        </div>
      )}
    </div>
  );
}
