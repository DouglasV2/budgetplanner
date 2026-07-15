import { useEffect, useRef, useState } from 'react';
import type { FurnishingPlan, PlannerInput, Product, RoomPriority, RoomType, SavedPlanResponse } from '../types';
import { generateMoveInPlan, adjustMoveInPlan, replaceProduct, savePlan, trackProductClick } from '../api/client';
import { formatCurrency } from '../utils/planner';
import { hydrateSession, serializeSession, clearForeignMarketRetained, retainedExceedsBudget, apartmentStatus, checklist, checklistTotals, type RoomPlanResult } from '../utils/moveInPlan';
import { useLocale } from '../LocaleContext';
import { RoomIcon, CategoryIcon, SwapIcon, CloseIcon, LockIcon, ExternalLinkIcon } from './icons';
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
// Sprint 10.183: the same key now holds the richer MoveInSession (adds priorities/retained/purchased/results);
// hydrateSession migrates the old {rooms,budget} shape transparently.
const MOVE_IN_DRAFT_KEY = 'budgetspace.moveInDraft';

function readMoveInDraft(): unknown {
  try {
    return JSON.parse(localStorage.getItem(MOVE_IN_DRAFT_KEY) || 'null');
  } catch {
    return null;
  }
}

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

// Sprint 10.183: the RoomPlanResult shape now lives in utils/moveInPlan.ts (imported above) so the pure session
// helpers and this component agree on it. The 3 priority levels map to their (human, hand-written) i18n labels.
const PRIORITY_LEVELS: RoomPriority[] = ['now', 'soon', 'later'];
const PRIORITY_LABEL_KEYS: Record<RoomPriority, string> = {
  now: 'moveIn.priorityNow',
  soon: 'moveIn.prioritySoon',
  later: 'moveIn.priorityLater',
};

// Sprint 10.183: the honest-note CODES the adjust endpoint returns → their localized i18n keys.
const ADJUST_MESSAGE_KEYS: Record<string, string> = {
  'reduce-unreachable': 'moveIn.adjustReduceUnreachable',
  'fewer-stores-noop': 'moveIn.adjustFewerStoresNoop',
  'use-remaining-done': 'moveIn.adjustUseRemainingDone',
  'use-remaining-none': 'moveIn.adjustUseRemainingNone',
};

// The 3 tiers come back with stable ids budget/value/stretch (plan.name is a Croatian display string).
// Sprint 10.155: match on the stable id, not the HR name, so a reworded plan.name can't silently break the
// default pick. For the apartment overview we default to the balanced "value" tier per room.
function pickBestPlan(plans: FurnishingPlan[]): FurnishingPlan | null {
  if (!plans.length) return null;
  return plans.find((plan) => plan.id === 'value') ?? plans[Math.min(1, plans.length - 1)] ?? plans[0];
}

export function MoveInPlanner({ baseInput, activeSpace, onSavedPlan, onNotice, seed }: MoveInPlannerProps) {
  const { t } = useLocale();
  const market = baseInput.market ?? 'HR';
  // Sprint 10.183: hydrate the whole-apartment session (rooms/budget/priorities today; retained/purchased/results
  // join it in later increments) from localStorage so a reload or return visit keeps the user's place.
  // hydrateSession migrates the old {rooms,budget} draft and drops market-specific state from another market.
  const [selectedRooms, setSelectedRooms] = useState<RoomType[]>(() => hydrateSession(readMoveInDraft(), market).rooms);
  const [totalBudget, setTotalBudget] = useState<number>(() => hydrateSession(readMoveInDraft(), market).budget);
  const [priorities, setPriorities] = useState<Partial<Record<RoomType, RoomPriority>>>(
    () => hydrateSession(readMoveInDraft(), market).priorities);
  const [swapping, setSwapping] = useState<string | null>(null);
  // Sprint 10.183: restore the last generated plan + the kept-room set from the session so a reload keeps the
  // whole apartment (kept PRODUCTS ride inside each room's input.lockedProductIds, restored with results).
  const [results, setResults] = useState<RoomPlanResult[] | null>(() => hydrateSession(readMoveInDraft(), market).results);
  const [retainedRooms, setRetainedRooms] = useState<RoomType[]>(() => hydrateSession(readMoveInDraft(), market).retainedRooms);
  const [apartmentPartial, setApartmentPartial] = useState(false);
  const [shortfall, setShortfall] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // Sprint 10.183 — adjust apartment: which action is in flight, the last honest note code, and the reduce-total
  // target (prefilled to the current plan total).
  const [adjusting, setAdjusting] = useState<string | null>(null);
  const [adjustNotice, setAdjustNotice] = useState<string | null>(null);
  const [reduceTarget, setReduceTarget] = useState<number>(0);
  // Sprint 10.183 — shopping checklist: product ids the user has ticked as bought (shopping progress, NOT
  // "already owned"). Persisted with the session so it survives a reload; market-scoped like the plan.
  const [purchasedIds, setPurchasedIds] = useState<string[]>(() => hydrateSession(readMoveInDraft(), market).purchasedIds);
  const togglePurchased = (productId: string) => setPurchasedIds((current) =>
    current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId]);
  const [showMissing, setShowMissing] = useState(false);

  // Sprint 10.116: apply a seed (from the multi-room nudge) — pre-select the detected rooms + typed budget.
  // SEO sprint: a mode=move-in landing preset may carry a budget without rooms (open the apartment scope but
  // don't invent a selection), so apply each part independently. Both parts override the hydrated draft — a URL
  // preset wins over a saved draft for the fields it set. The multi-room nudge always carries rooms + a budget,
  // so its behaviour is unchanged.
  useEffect(() => {
    if (!seed) return;
    if (seed.rooms.length) setSelectedRooms(seed.rooms);
    if (seed.budget > 0) setTotalBudget(seed.budget);
  }, [seed]);

  // Sprint 10.183: persist the whole-apartment session so the next visit starts where the user left off — room
  // ids, numbers, priority levels, the kept-room set and the generated plan (which carries the kept-product ids
  // inside each room's input.lockedProductIds). No PII. Market-specific parts are dropped on load when the
  // market differs (hydrateSession). purchasedIds join this in the checklist increment.
  useEffect(() => {
    try {
      localStorage.setItem(MOVE_IN_DRAFT_KEY, serializeSession({
        market, rooms: selectedRooms, budget: totalBudget, priorities, retainedRooms, results, purchasedIds,
      }));
    } catch { /* ignore quota/private-mode */ }
  }, [market, selectedRooms, totalBudget, priorities, retainedRooms, results, purchasedIds]);

  function toggleRoom(room: RoomType) {
    setSelectedRooms((current) => (current.includes(room) ? current.filter((value) => value !== room) : [...current, room]));
  }

  const priorityOf = (room: RoomType): RoomPriority => priorities[room] ?? 'soon';

  function setPriority(room: RoomType, level: RoomPriority) {
    setPriorities((current) => ({ ...current, [room]: level }));
  }

  // Sprint 10.183 — keep a room: a preference that shields the room from whole-plan adjustments (Feature 3).
  const isRoomRetained = (room: RoomType) => retainedRooms.includes(room);
  function toggleRetainedRoom(room: RoomType) {
    setRetainedRooms((current) => (current.includes(room) ? current.filter((value) => value !== room) : [...current, room]));
  }

  // Sprint 10.183 — keep a product: reuse the backend-honoured lockedProductIds seam, per room. A kept product
  // is pinned (won't be swapped by adjustments) but can still be explicitly removed or un-kept by the user.
  function isProductKept(roomType: RoomType, productId: string): boolean {
    const room = results?.find((result) => result.roomType === roomType);
    return !!room?.input.lockedProductIds.includes(productId);
  }
  function toggleKeepProduct(roomType: RoomType, productId: string) {
    setResults((prev) => prev && prev.map((room) => {
      if (room.roomType !== roomType) return room;
      const locked = room.input.lockedProductIds.includes(productId)
        ? room.input.lockedProductIds.filter((id) => id !== productId)
        : [...room.input.lockedProductIds, productId];
      return { ...room, input: { ...room.input, lockedProductIds: locked } };
    }));
  }

  // Sprint 10.183: on an ACTUAL market change, strip kept PRODUCTS that belong to the old market (never carry a
  // foreign-market SKU into the new market) and say so. Kept ROOMS are a market-agnostic preference and survive.
  const marketRef = useRef(market);
  useEffect(() => {
    if (marketRef.current === market) return;
    marketRef.current = market;
    setResults((prev) => {
      const { results: cleaned, cleared } = clearForeignMarketRetained(prev, market);
      if (cleared > 0) onNotice(t('moveIn.retainedMarketCleared'));
      return cleaned;
    });
  }, [market, onNotice, t]);

  // Sprint 10.183: honest guard — if the items the user chose to keep already cost more than the budget, say so
  // (near the budget field) instead of silently dropping them or letting the plan run over.
  const keepExceedsBudget = retainedExceedsBudget(results, retainedRooms, totalBudget);

  // Sprint 10.183: prefill the reduce-total target with the current plan total whenever a fresh plan lands.
  useEffect(() => {
    if (results) setReduceTarget(Math.round(results.reduce((sum, room) => sum + room.plan.total, 0)));
  }, [results]);

  // Sprint 10.183 — adjust the whole apartment: send the current rooms (+ their retained/kept state) and an
  // action to the backend, then splice the returned rooms back in. Kept rooms/products come back untouched.
  async function runAdjust(action: string, targetTotal: number | null) {
    if (!results || adjusting) return;
    setAdjusting(action);
    setAdjustNotice(null);
    try {
      const payloadRooms = results.map((room) => ({
        roomType: room.roomType,
        plan: room.plan,
        retained: retainedRooms.includes(room.roomType),
        lockedProductIds: room.input.lockedProductIds,
      }));
      const roomPriority: Partial<Record<RoomType, RoomPriority>> = {};
      for (const room of results) roomPriority[room.roomType] = priorityOf(room.roomType);
      const response = await adjustMoveInPlan(baseInput, payloadRooms, totalBudget, action, targetTotal, roomPriority);
      const byRoom = new Map(response.rooms.map((room) => [room.roomType, room] as const));
      setResults((prev) => prev && prev.map((room) => {
        const updated = byRoom.get(room.roomType);
        const plan = updated ? pickBestPlan(updated.plans) : null;
        if (!plan) return room;
        // The backend response carries each room's kept-product ids back on the plan's items only, so keep the
        // client-side lockedProductIds authoritative (they are what the next adjust/keep toggle reads). Refresh
        // the honest missing-item buckets from the adjusted response.
        return {
          ...room, plan, hasItems: plan.items.length > 0,
          missingEssential: updated?.missingEssential, niceToHave: updated?.niceToHave, unavailableInMarket: updated?.unavailableInMarket,
        };
      }));
      if (response.changed === false || response.message) setAdjustNotice(response.message ?? 'no-change');
    } catch {
      onNotice(t('moveIn.error'));
    } finally {
      setAdjusting(null);
    }
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
    // !(x > 0) catches 0, negatives AND NaN (a cleared number input can leave totalBudget NaN, and NaN <= 0
    // is false — which would slip past a `<= 0` check).
    if (!(totalBudget > 0)) {
      setError(t('moveIn.budgetRequired'));
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
      // Sprint 10.183: send each selected room's priority (default 'soon' = neutral) so the split favours what
      // the user needs first.
      const roomPriority: Partial<Record<RoomType, RoomPriority>> = {};
      for (const room of orderedRooms) roomPriority[room] = priorityOf(room);
      const response = await generateMoveInPlan(baseInput, orderedRooms, totalBudget, roomPriority);

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
            partial: room.partial,
            missingEssential: room.missingEssential,
            niceToHave: room.niceToHave,
            unavailableInMarket: room.unavailableInMarket
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
  // Sprint 10.183: whole-apartment status + shopping checklist (all honest, derived from the real plan).
  const status = apartmentStatus(results, totalBudget);
  const checkGroups = checklist(results);
  const checkTotals = checklistTotals(results, purchasedIds);
  const hasMissing = status.missing.moveIn.length + status.missing.niceToHave.length + status.missing.notFound.length > 0;
  const roomLabel = (room: RoomType) => t(MOVE_IN_ROOMS.find((entry) => entry.value === room)?.labelKey ?? '');
  const categoryLabels = (categories: string[]) => categories.map((category) => {
    const label = t(`cat.${category}`);
    return label.startsWith('cat.') ? category : label;
  }).join(', ');
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
      <div className="form-step move-in-form move-in-form-split">
        {/* Heading + intro sit as a right-hand aside on wide screens so the budget
            + room controls rise to the top instead of a full-width heading row
            eating vertical space; on narrow screens it stacks (heading first). */}
        <div className="move-in-form-aside">
          <h3>{t('moveIn.heading')}</h3>
          <p>{t('moveIn.intro')}</p>
        </div>

        <div className="move-in-form-main">
        <div className="control-block budget-block">
          <span className="friendly-label">{t('moveIn.totalBudgetLabel')}</span>
          {/* value={totalBudget || ''} shows empty (not a stuck "0") when cleared — a plain value={number}
              forced a leading zero you couldn't delete (clear 5000 -> 0 -> "0", then typing gave "07000").
              Mirrors the single-room budget field. */}
          <label className="budget-input-wrap">
            <input
              aria-label={t('moveIn.totalBudgetLabel')}
              type="number"
              inputMode="numeric"
              min="200"
              step="100"
              value={totalBudget || ''}
              onChange={(event) => setTotalBudget(Math.min(10_000_000, Math.max(0, Math.floor(Number(event.target.value) || 0))))}
            />
            <span>€</span>
          </label>
          {keepExceedsBudget && (
            <p className="move-in-keep-warning" role="status">{t('moveIn.retainedExceedsBudget')}</p>
          )}
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

        {/* Sprint 10.183: "what do you need first?" — a priority per selected room that steers the budget split. */}
        {selectedRooms.length > 0 && (
          <div className="control-block move-in-priorities">
            <span className="friendly-label">{t('moveIn.priorityHeading')} <small>{t('moveIn.priorityHelp')}</small></span>
            <ul className="move-in-priority-list">
              {MOVE_IN_ROOMS.filter((room) => selectedRooms.includes(room.value)).map((room) => (
                <li key={room.value} className="move-in-priority-row">
                  <span className="move-in-priority-room">
                    <span className="move-in-priority-roomicon" aria-hidden="true"><RoomIcon room={room.value} size={16} /></span>
                    {t(room.labelKey)}
                  </span>
                  <span className="move-in-priority-options" role="group" aria-label={t(room.labelKey)}>
                    {PRIORITY_LEVELS.map((level) => (
                      <button
                        type="button"
                        key={level}
                        className={priorityOf(room.value) === level ? 'priority-opt active' : 'priority-opt'}
                        aria-pressed={priorityOf(room.value) === level}
                        onClick={() => setPriority(room.value, level)}
                      >
                        {t(PRIORITY_LABEL_KEYS[level])}
                      </button>
                    ))}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <button type="button" className="generate-button" disabled={isLoading} onClick={() => void runMoveIn()}>
          {isLoading ? t('moveIn.generating') : t('moveIn.generate')}
        </button>
        {error && <p className="planner-notice" role="alert">{error}</p>}
        {!results && !error && <p className="move-in-empty-hint">{t('moveIn.emptyHint')}</p>}
        </div>
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

            {/* Sprint 10.183: honest apartment status — rooms, stores, remaining, and what still needs attention. */}
            <div className="move-in-status-grid">
              <span className="move-in-status-cell">{t('moveIn.statusRooms', { count: status.roomCount })}</span>
              <span className="move-in-status-cell">{t('moveIn.storesCount', { count: status.retailerCount })}</span>
              {over <= 0 && <span className="move-in-status-cell within">{t('moveIn.statusRemaining', { amount: formatCurrency(status.remaining) })}</span>}
            </div>
            {status.attentionRooms.length > 0 && (
              <p className="move-in-status-line">
                <span className="move-in-status-tag warn">{t('moveIn.stillToSolve')}</span> {status.attentionRooms.map(roomLabel).join(', ')}
              </p>
            )}
            {status.coveredRooms.length > 0 && (
              <p className="move-in-status-line">
                <span className="move-in-status-tag ok">{t('moveIn.roomsCovered')}</span> {status.coveredRooms.map(roomLabel).join(', ')}
              </p>
            )}
            {hasMissing && (
              <div className="move-in-missing">
                <button type="button" className="move-in-missing-toggle" aria-expanded={showMissing} onClick={() => setShowMissing((value) => !value)}>
                  {t('moveIn.showMissing')}
                </button>
                {showMissing && (
                  <ul className="move-in-missing-groups">
                    {status.missing.moveIn.length > 0 && (
                      <li><span className="move-in-missing-label">{t('moveIn.missingMoveIn')}</span><span>{categoryLabels(status.missing.moveIn)}</span></li>
                    )}
                    {status.missing.niceToHave.length > 0 && (
                      <li><span className="move-in-missing-label">{t('moveIn.missingNiceToHave')}</span><span>{categoryLabels(status.missing.niceToHave)}</span></li>
                    )}
                    {status.missing.notFound.length > 0 && (
                      <li><span className="move-in-missing-label nf">{t('moveIn.missingNotFound')}</span><span>{categoryLabels(status.missing.notFound)}</span></li>
                    )}
                  </ul>
                )}
              </div>
            )}
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

          {/* Sprint 10.183: adjust the whole apartment — reduce total / fewer stores / use remaining. */}
          <div className="move-in-adjust no-print">
            <span className="move-in-adjust-title">{t('moveIn.adjustHeading')}</span>
            <p className="move-in-adjust-hint">{t('moveIn.adjustHint')}</p>
            <div className="move-in-adjust-actions">
              <div className="move-in-adjust-reduce">
                <label className="move-in-adjust-target">
                  <input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="50"
                    aria-label={t('moveIn.adjustReduce')}
                    value={reduceTarget || ''}
                    onChange={(event) => setReduceTarget(Math.max(0, Math.floor(Number(event.target.value) || 0)))}
                  />
                  <span>€</span>
                </label>
                <button type="button" className="move-in-adjust-btn" disabled={!!adjusting}
                  onClick={() => void runAdjust('reduce-total', reduceTarget)}>{t('moveIn.adjustReduce')}</button>
              </div>
              <button type="button" className="move-in-adjust-btn" disabled={!!adjusting}
                onClick={() => void runAdjust('fewer-stores', null)}>{t('moveIn.adjustFewerStores')}</button>
              <button type="button" className="move-in-adjust-btn" disabled={!!adjusting}
                onClick={() => void runAdjust('use-remaining', null)}>{t('moveIn.adjustUseRemaining')}</button>
            </div>
            {adjusting && <p className="move-in-adjust-status" role="status">{t('moveIn.adjusting')}</p>}
            {adjustNotice && !adjusting && (
              <p className="move-in-adjust-note" role="status">{t(ADJUST_MESSAGE_KEYS[adjustNotice] ?? 'moveIn.adjustNoChange')}</p>
            )}
          </div>

          <div className="move-in-room-cards">
            {results.map((result) => (
              <article className={isRoomRetained(result.roomType) ? 'move-in-room-card retained' : 'move-in-room-card'} key={result.roomType}>
                <div className="move-in-room-head">
                  <span className="move-in-room-title">
                    <span className="move-in-room-roomicon" aria-hidden="true"><RoomIcon room={result.roomType} size={17} /></span>
                    <strong>{t(result.labelKey)}</strong>
                  </span>
                  <span className="move-in-room-headmeta">
                    {result.hasItems && (
                      <span className="move-in-room-count">{t('moveIn.itemsCount', { count: result.plan.items.length })}</span>
                    )}
                    {result.hasItems && (
                      <button
                        type="button"
                        className={isRoomRetained(result.roomType) ? 'move-in-keep-room kept' : 'move-in-keep-room'}
                        aria-pressed={isRoomRetained(result.roomType)}
                        title={t('moveIn.keepRoomHint')}
                        onClick={() => toggleRetainedRoom(result.roomType)}
                      >
                        <LockIcon size={13} />
                        {isRoomRetained(result.roomType) ? t('moveIn.unlockRoom') : t('moveIn.keepRoom')}
                      </button>
                    )}
                  </span>
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
                        const kept = isProductKept(result.roomType, item.product.id);
                        const thumb = (
                          <span className="move-in-thumb" aria-hidden="true">
                            {photo
                              ? <img src={photo} alt="" loading="lazy" referrerPolicy="no-referrer" />
                              : <CategoryIcon category={item.product.category} size={24} />}
                          </span>
                        );
                        return (
                          <li key={item.product.id} className={kept ? 'move-in-item kept' : 'move-in-item'}>
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
                            {/* Sprint 10.138: swap / drop; 10.183: keep — not buttons inside the <a> (invalid). */}
                            <span className="move-in-item-actions no-print">
                              <button
                                type="button"
                                className={kept ? 'move-in-item-act move-in-item-keep kept' : 'move-in-item-act move-in-item-keep'}
                                aria-pressed={kept}
                                title={kept ? t('moveIn.unlockProduct') : t('moveIn.keepProductHint')}
                                aria-label={kept ? t('moveIn.unlockProduct') : t('moveIn.keepProduct')}
                                onClick={() => toggleKeepProduct(result.roomType, item.product.id)}
                              >
                                <LockIcon size={14} />
                              </button>
                              <button
                                type="button"
                                className="move-in-item-act"
                                title={kept ? t('moveIn.keptSwapOff') : t('moveIn.swapItem')}
                                aria-label={t('moveIn.swapItem')}
                                disabled={kept || swapping === item.product.id}
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

          {/* Sprint 10.183: "Popis za kupnju" — an interactive shopping checklist grouped by retailer. Ticking a
              box marks a piece as bought (shopping progress) without removing it from the plan. */}
          {checkGroups.length > 0 && (
            <div className="move-in-checklist">
              <div className="move-in-checklist-head">
                <span className="move-in-checklist-title">{t('moveIn.shoppingListHeading')}</span>
                <span className="move-in-checklist-totals">
                  <span className="bought">{t('moveIn.bought')}: {formatCurrency(checkTotals.bought)}</span>
                  <span className="remaining">{t('moveIn.stillToBuy')}: {formatCurrency(checkTotals.remaining)}</span>
                </span>
              </div>
              {checkGroups.map((group) => (
                <div className="move-in-checklist-store" key={group.retailer}>
                  <div className="move-in-checklist-store-head">
                    <span className="move-in-checklist-store-name">{group.retailer}</span>
                    <span className="move-in-checklist-store-meta">{t('moveIn.itemsCount', { count: group.count })} · {formatCurrency(group.total)}</span>
                  </div>
                  <ul className="move-in-checklist-items">
                    {group.items.map((entry) => {
                      const openUrl = storeUrl(entry.product);
                      const bought = purchasedIds.includes(entry.productId);
                      return (
                        <li key={entry.productId} className={bought ? 'move-in-check-item bought' : 'move-in-check-item'}>
                          <label className="move-in-check-label">
                            <input type="checkbox" checked={bought} onChange={() => togglePurchased(entry.productId)} aria-label={entry.name} />
                            <span className="move-in-check-name">{entry.name}</span>
                          </label>
                          <span className="move-in-check-room">{roomLabel(entry.roomType)}</span>
                          <span className="move-in-check-price">{formatCurrency(entry.lineTotal)}</span>
                          {openUrl ? (
                            <a
                              className="move-in-check-open"
                              href={openUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              title={t('results.openInStore')}
                              aria-label={t('results.openInStore')}
                              onClick={() => trackProductClick(entry.planId, entry.product)}
                            >
                              <ExternalLinkIcon />
                            </a>
                          ) : <span className="move-in-check-open-empty" aria-hidden="true" />}
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
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
