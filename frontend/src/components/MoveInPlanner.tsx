import { useState } from 'react';
import type { FurnishingPlan, PlannerInput, RoomType, SavedPlanResponse } from '../types';
import { generatePlanFast, savePlan } from '../api/client';
import { allocateBudget, grandTotal } from '../utils/moveIn';
import { formatCurrency } from '../utils/planner';
import { useLocale } from '../LocaleContext';

// Sprint 10.109: Move-In ("Cijeli stan") — the apartment branch of the planner. It is fully self-contained
// and reuses the EXISTING single-room engine: split one total budget across the chosen rooms (allocateBudget),
// then call the normal /generate-fast once per room. The single-room flow in Planner.tsx is untouched.

interface MoveInPlannerProps {
  // The current single-room form input — we inherit its shared settings (style, stores, market, location, size).
  baseInput: PlannerInput;
  // The active "space" (home) that saved rooms group under, e.g. "Moj dom".
  activeSpace: string;
  onSavedPlan: (plan: SavedPlanResponse) => void;
  onNotice: (message: string) => void;
}

const MOVE_IN_ROOMS: Array<{ value: RoomType; labelKey: string; icon: string }> = [
  { value: 'living-room', labelKey: 'form.roomLivingRoomLabel', icon: '🛋️' },
  { value: 'bedroom', labelKey: 'form.roomBedroomLabel', icon: '🛏️' },
  { value: 'home-office', labelKey: 'form.roomHomeOfficeLabel', icon: '💻' },
  { value: 'dining-room', labelKey: 'form.roomDiningRoomLabel', icon: '🍽️' },
  { value: 'kitchen', labelKey: 'form.roomKitchenLabel', icon: '🍳' },
  { value: 'hallway', labelKey: 'form.roomHallwayLabel', icon: '🚪' },
  { value: 'bathroom', labelKey: 'form.roomBathroomLabel', icon: '🛁' }
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

// The 3 tiers come back as "Najjeftinije" / "Najbolji izbor" / "Ljepša verzija" (backend names are stable HR).
// For the apartment overview we default to the balanced "best value" tier per room.
function pickBestPlan(plans: FurnishingPlan[]): FurnishingPlan | null {
  if (!plans.length) return null;
  return plans.find((plan) => plan.name === 'Najbolji izbor') ?? plans[Math.min(1, plans.length - 1)] ?? plans[0];
}

export function MoveInPlanner({ baseInput, activeSpace, onSavedPlan, onNotice }: MoveInPlannerProps) {
  const { t } = useLocale();
  const [selectedRooms, setSelectedRooms] = useState<RoomType[]>(['living-room', 'bedroom']);
  const [totalBudget, setTotalBudget] = useState<number>(5000);
  const [results, setResults] = useState<RoomPlanResult[] | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function toggleRoom(room: RoomType) {
    setSelectedRooms((current) => (current.includes(room) ? current.filter((value) => value !== room) : [...current, room]));
  }

  function buildRoomInput(roomType: RoomType, budget: number): PlannerInput {
    const roomName = t(MOVE_IN_ROOMS.find((room) => room.value === roomType)?.labelKey ?? '');
    return {
      ...baseInput,
      roomType,
      budget,
      // A clean, self-consistent prompt: gives the rule-based extractor context but never contradicts roomType.
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
    try {
      // Keep a stable top-down order (by the picker list), independent of click order.
      const orderedRooms = MOVE_IN_ROOMS.filter((room) => selectedRooms.includes(room.value)).map((room) => room.value);
      const allocations = allocateBudget(totalBudget, orderedRooms);

      const settled = await Promise.all(allocations.map(async (allocation) => {
        const input = buildRoomInput(allocation.roomType, allocation.budget);
        try {
          const response = await generatePlanFast(input);
          const plan = pickBestPlan(response.plans);
          if (!plan) return null;
          return {
            roomType: allocation.roomType,
            labelKey: MOVE_IN_ROOMS.find((room) => room.value === allocation.roomType)?.labelKey ?? '',
            allocatedBudget: allocation.budget,
            plan,
            input,
            hasItems: plan.items.length > 0,
            partial: !!response.partialPlan
          } as RoomPlanResult;
        } catch {
          return null;
        }
      }));

      const ok = settled.filter((result): result is RoomPlanResult => result !== null);
      if (!ok.length) {
        setError(t('moveIn.error'));
        return;
      }
      setResults(ok);
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

  const total = grandTotal(results?.map((result) => result.plan.total) ?? []);
  const over = total - totalBudget;

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
              min="200"
              step="100"
              value={totalBudget}
              onChange={(event) => setTotalBudget(Number(event.target.value || 0))}
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
                <span>{room.icon}</span>
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
          <div className="move-in-total-card">
            <span className="move-in-total-label">{t('moveIn.grandTotalLabel')}</span>
            <strong className="move-in-total-value">{formatCurrency(total)}</strong>
            <span className="move-in-total-budget">{t('moveIn.ofBudget', { budget: formatCurrency(totalBudget) })}</span>
            <span className={over > 0 ? 'move-in-status over' : 'move-in-status within'}>
              {over > 0 ? t('moveIn.overBudget', { amount: formatCurrency(over) }) : t('moveIn.withinBudget')}
            </span>
          </div>

          <div className="move-in-room-cards">
            {results.map((result) => (
              <article className="move-in-room-card" key={result.roomType}>
                <div className="move-in-room-head">
                  <strong>{t(result.labelKey)}</strong>
                  <span className="move-in-room-budget">{t('moveIn.roomBudget', { amount: formatCurrency(result.allocatedBudget) })}</span>
                </div>
                {result.hasItems ? (
                  <>
                    <div className="move-in-room-meta">
                      <span className="move-in-room-total">{formatCurrency(result.plan.total)}</span>
                      <span className="move-in-room-count">{t('moveIn.itemsCount', { count: result.plan.items.length })}</span>
                      {result.plan.retailersUsed.length > 0 && (
                        <span className="move-in-room-stores">{result.plan.retailersUsed.join(' + ')}</span>
                      )}
                    </div>
                    <ul className="move-in-room-items">
                      {result.plan.items.slice(0, 4).map((item) => (
                        <li key={item.product.id}>
                          <span>{item.product.name}</span>
                          <span>{formatCurrency(item.product.price)}</span>
                        </li>
                      ))}
                      {result.plan.items.length > 4 && <li className="move-in-more">+{result.plan.items.length - 4}</li>}
                    </ul>
                    {result.partial && <small className="move-in-room-partial">{t('moveIn.partialRoom')}</small>}
                  </>
                ) : (
                  <p className="move-in-room-empty">{t('moveIn.noProducts')}</p>
                )}
              </article>
            ))}
          </div>

          <button type="button" className="generate-button move-in-save" disabled={saving} onClick={() => void saveApartment()}>
            {t('moveIn.saveApartment')}
          </button>
        </div>
      )}
    </div>
  );
}
