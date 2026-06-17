import { useState } from 'react';
import type { FurnishingLevel, OptimizationGoal, PlannerInput, ProductCategory, Retailer, RoomType, StyleType } from '../types';
import { categoryLabels, formatCurrency, retailers } from '../utils/planner';
import { useLocale } from '../LocaleContext';

interface PlannerFormProps {
  input: PlannerInput;
  onChange: (input: PlannerInput) => void;
  onGenerate: () => void;
  isLoading?: boolean;
}

const rooms: Array<{ value: RoomType; label: string; icon: string; hint: string }> = [
  { value: 'living-room', label: 'form.roomLivingRoomLabel', icon: '🛋️', hint: 'form.roomLivingRoomHint' },
  { value: 'home-office', label: 'form.roomHomeOfficeLabel', icon: '💻', hint: 'form.roomHomeOfficeHint' },
  { value: 'bedroom', label: 'form.roomBedroomLabel', icon: '🛏️', hint: 'form.roomBedroomHint' },
  { value: 'home-gym', label: 'form.roomHomeGymLabel', icon: '🏋️', hint: 'form.roomHomeGymHint' },
  { value: 'dining-room', label: 'form.roomDiningRoomLabel', icon: '🍽️', hint: 'form.roomDiningRoomHint' },
  { value: 'kitchen', label: 'form.roomKitchenLabel', icon: '🍳', hint: 'form.roomKitchenHint' },
  { value: 'hallway', label: 'form.roomHallwayLabel', icon: '🚪', hint: 'form.roomHallwayHint' },
  { value: 'bathroom', label: 'form.roomBathroomLabel', icon: '🛁', hint: 'form.roomBathroomHint' }
];

const styles: Array<{ value: StyleType; label: string; hint: string }> = [
  { value: 'surprise', label: 'form.styleSurpriseLabel', hint: 'form.styleSurpriseHint' },
  { value: 'bright', label: 'form.styleBrightLabel', hint: 'form.styleBrightHint' },
  { value: 'warm', label: 'form.styleWarmLabel', hint: 'form.styleWarmHint' },
  { value: 'modern', label: 'form.styleModernLabel', hint: 'form.styleModernHint' },
  { value: 'minimal', label: 'form.styleMinimalLabel', hint: 'form.styleMinimalHint' },
  { value: 'classic', label: 'form.styleClassicLabel', hint: 'form.styleClassicHint' },
  { value: 'industrial', label: 'form.styleIndustrialLabel', hint: 'form.styleIndustrialHint' },
  { value: 'boho', label: 'form.styleBohoLabel', hint: 'form.styleBohoHint' }
];

const furnishingLevels: Array<{ value: FurnishingLevel; label: string; description: string }> = [
  { value: 'basic', label: 'form.furnishingBasicLabel', description: 'form.furnishingBasicDescription' },
  { value: 'comfort', label: 'form.furnishingComfortLabel', description: 'form.furnishingComfortDescription' },
  { value: 'complete', label: 'form.furnishingCompleteLabel', description: 'form.furnishingCompleteDescription' }
];

const optimizationGoals: Array<{ value: OptimizationGoal; label: string; description: string }> = [
  { value: 'best-value', label: 'form.goalBestValueLabel', description: 'form.goalBestValueDescription' },
  { value: 'lowest-price', label: 'form.goalLowestPriceLabel', description: 'form.goalLowestPriceDescription' },
  { value: 'least-stores', label: 'form.goalLeastStoresLabel', description: 'form.goalLeastStoresDescription' },
  { value: 'style-match', label: 'form.goalStyleMatchLabel', description: 'form.goalStyleMatchDescription' }
];

const categoryOrder: ProductCategory[] = [
  'sofa',
  'tv-unit',
  'table',
  'rug',
  'lighting',
  'storage',
  'decor',
  'desk',
  'chair',
  'bed',
  'mattress',
  'gym-equipment'
];

const categoryOrderByRoom: Record<RoomType, ProductCategory[]> = {
  'living-room': ['sofa', 'tv-unit', 'table', 'rug', 'lighting', 'storage', 'decor'],
  'home-office': ['desk', 'chair', 'storage', 'lighting', 'decor'],
  bedroom: ['bed', 'mattress', 'nightstand', 'wardrobe', 'dresser', 'storage', 'lighting', 'rug', 'decor'],
  'home-gym': ['gym-equipment', 'storage', 'lighting', 'decor'],
  kitchen: ['kitchen-cart', 'kitchen-storage', 'lighting', 'storage', 'decor'],
  'dining-room': ['dining-table', 'dining-chair', 'lighting', 'rug', 'storage', 'decor'],
  hallway: ['storage', 'lighting', 'rug', 'decor'],
  bathroom: ['storage', 'lighting', 'decor']
};

const sizePresets = [
  { label: 'form.sizeUnknownLabel', size: 20, description: 'form.sizeUnknownDescription' },
  { label: 'form.sizeSmallLabel', size: 12, description: 'form.sizeSmallDescription' },
  { label: 'form.sizeMediumLabel', size: 20, description: 'form.sizeMediumDescription' },
  { label: 'form.sizeLargeLabel', size: 32, description: 'form.sizeLargeDescription' }
];

const budgetPresets = [700, 1000, 1500, 2500];

const starterTemplates: Array<{ title: string; subtitle: string; promptKey: string; input: Partial<PlannerInput> }> = [
  {
    title: 'form.templateLivingRoomTitle',
    subtitle: 'form.templateLivingRoomSubtitle',
    promptKey: 'form.templateLivingRoomPrompt',
    input: {
      budget: 1500,
      roomType: 'living-room',
      style: 'bright',
      size: 20,
      selectedRetailers: ['IKEA', 'JYSK'],
      retailerMode: 'multi',
      furnishingLevel: 'comfort',
      mustHaveCategories: ['sofa', 'tv-unit', 'table', 'rug', 'lighting'],
      alreadyHaveCategories: []
    }
  },
  {
    title: 'form.templateHomeOfficeTitle',
    subtitle: 'form.templateHomeOfficeSubtitle',
    promptKey: 'form.templateHomeOfficePrompt',
    input: {
      budget: 800,
      roomType: 'home-office',
      style: 'minimal',
      size: 12,
      selectedRetailers: ['IKEA', 'JYSK', 'Pevex'],
      retailerMode: 'multi',
      furnishingLevel: 'comfort',
      mustHaveCategories: ['desk', 'chair', 'storage', 'lighting'],
      alreadyHaveCategories: []
    }
  },
  {
    title: 'form.templateHomeGymTitle',
    subtitle: 'form.templateHomeGymSubtitle',
    promptKey: 'form.templateHomeGymPrompt',
    input: {
      budget: 1200,
      roomType: 'home-gym',
      style: 'modern',
      size: 16,
      selectedRetailers: ['Decathlon', 'Pevex'],
      retailerMode: 'multi',
      furnishingLevel: 'basic',
      mustHaveCategories: ['gym-equipment', 'storage', 'lighting'],
      alreadyHaveCategories: []
    }
  },
  {
    title: 'form.templateIkeaOnlyTitle',
    subtitle: 'form.templateIkeaOnlySubtitle',
    promptKey: 'form.templateIkeaOnlyPrompt',
    input: {
      budget: 1800,
      roomType: 'living-room',
      style: 'bright',
      size: 22,
      selectedRetailers: ['IKEA'],
      retailerMode: 'single',
      optimizationGoal: 'least-stores',
      furnishingLevel: 'complete',
      mustHaveCategories: ['sofa', 'tv-unit', 'table', 'rug', 'lighting'],
      alreadyHaveCategories: []
    }
  }
];

function toggle<T>(items: T[], item: T) {
  return items.includes(item) ? items.filter((current) => current !== item) : [...items, item];
}

function setRetailer(input: PlannerInput, retailer: Retailer) {
  if (input.retailerMode === 'single') {
    return { ...input, selectedRetailers: [retailer] };
  }

  const nextRetailers = toggle(input.selectedRetailers, retailer);
  return { ...input, selectedRetailers: nextRetailers.length ? nextRetailers : [retailer] };
}

function applyTemplate(input: PlannerInput, template: Partial<PlannerInput>, prompt: string): PlannerInput {
  return {
    ...input,
    ...template,
    prompt,
    lockedProductIds: []
  };
}

function selectedShopMode(input: PlannerInput) {
  if (input.retailerMode === 'single') return 'one-store';
  if (input.selectedRetailers.length === retailers.length) return 'best-combo';
  return 'choose-stores';
}

function visibleCategories(input: PlannerInput) {
  return Array.from(new Set([
    ...(categoryOrderByRoom[input.roomType] ?? categoryOrder),
    ...input.mustHaveCategories,
    ...input.alreadyHaveCategories
  ]));
}

function normalizeText(value: string) {
  return value.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
}

function categoriesFromText(value: string): ProductCategory[] {
  const text = normalizeText(value);
  const matches: Array<[ProductCategory, RegExp]> = [
    ['sofa', /\b(kauc|sofa|trosjed|garnitura)\b/],
    ['tv-unit', /\b(tv komoda|tv element|komoda|tv)\b/],
    ['table', /\b(stolic(?!a)|klub stol|coffee table)\b/],
    ['rug', /\btepih\b/],
    ['lighting', /\b(lampa|rasvjeta|svjetlo)\b/],
    ['storage', /\b(polica|regal|ormar|spremanje)\b/],
    ['decor', /\b(dekor|slika|jastuk|biljka)\b/],
    ['desk', /\b(radni stol|desk)\b/],
    ['chair', /\b(stolica|chair)\b/],
    ['bed', /\b(krevet|bed)\b/],
    ['mattress', /\b(madrac|mattress)\b/],
    ['gym-equipment', /\b(bucice|bench|klupa|utezi|sprava)\b/]
  ];
  return matches.filter(([, pattern]) => pattern.test(text)).map(([category]) => category);
}

export function PlannerForm({ input, onChange, onGenerate, isLoading = false }: PlannerFormProps) {
  const { t } = useLocale();
  const [alreadyHaveFreeText, setAlreadyHaveFreeText] = useState('');
  const shopMode = selectedShopMode(input);
  const visibleCategoryOptions = visibleCategories(input);
  const alreadyHaveText = input.alreadyHaveCategories.map((category) => categoryLabels[category]).join(", ");

  return (
    <form
      className="planner-form friendly-form"
      onSubmit={(event) => {
        event.preventDefault();
        onGenerate();
      }}
    >
      <div className="form-step prompt-card prompt-first-card">
        <div className="step-kicker">{t('form.stepDescribeKicker')}</div>
        <div className="prompt-topline">
          <div>
            <h3>{t('form.describeHeading')}</h3>
            <p>{t('form.describeIntro')}</p>
          </div>
          <span className="ai-chip">{t('form.easiestChip')}</span>
        </div>
        <label>
          <span>{t('form.promptLabel')}</span>
          <textarea
            aria-label={t('form.promptAriaLabel')}
            rows={7}
            value={input.prompt}
            placeholder={t('form.promptPlaceholder')}
            onChange={(event) => onChange({ ...input, prompt: event.target.value })}
          />
        </label>
        <button className="generate-button" type="submit" disabled={isLoading}>
          {isLoading ? t('planner.generating') : t('planner.generate')}
          <span>{t('form.generateHint')}</span>
        </button>
      </div>

      <div className="form-step starter-template-panel">
        <div className="step-kicker">{t('form.quickStartKicker')}</div>
        <h3>{t('form.quickStartHeading')}</h3>
        <p>{t('form.quickStartIntro')}</p>
        <div className="template-grid">
          {starterTemplates.map((template) => (
            <button type="button" className="template-card" key={template.title} onClick={() => onChange(applyTemplate(input, template.input, t(template.promptKey)))}>
              <strong>{t(template.title)}</strong>
              <span>{t(template.subtitle)}</span>
            </button>
          ))}
        </div>
      </div>

      <details className="advanced-settings">
        <summary>
          <span>{t('form.advancedSummary')}</span>
          <small>{t('form.advancedSummaryHint')}</small>
        </summary>

      <div className="form-step easy-controls">
        <div className="step-kicker">{t('form.adjustKicker')}</div>
        <h3>{t('form.basicsHeading')}</h3>
        <p>{t('form.basicsIntro')}</p>

        <div className="control-block budget-block">
          <span className="friendly-label">{t('form.budgetLabel')}</span>
          <label className="budget-input-wrap">
            <input
              aria-label={t('form.budgetAriaLabel')}
              type="number"
              min="100"
              step="50"
              value={input.budget}
              onChange={(event) => onChange({ ...input, budget: Number(event.target.value || 0) })}
            />
            <span>€</span>
          </label>
          <div className="budget-presets" aria-label={t('form.budgetPresetsAriaLabel')}>
            {budgetPresets.map((budget) => (
              <button type="button" key={budget} className={input.budget === budget ? 'preset active' : 'preset'} onClick={() => onChange({ ...input, budget })}>
                {formatCurrency(budget)}
              </button>
            ))}
          </div>
        </div>

        <div className="control-block">
          <span className="friendly-label">{t('form.whatFurnishLabel')}</span>
          <div className="choice-grid rooms friendly-rooms">
            {rooms.map((room) => (
              <button
                type="button"
                className={input.roomType === room.value ? 'choice active' : 'choice'}
                key={room.value}
                onClick={() => onChange({ ...input, roomType: room.value })}
              >
                <span>{room.icon}</span>
                <strong>{t(room.label)}</strong>
                <small>{t(room.hint)}</small>
              </button>
            ))}
          </div>
        </div>

        <div className="control-block">
          <span className="friendly-label">{t('form.roomSizeLabel')}</span>
          <div className="size-grid">
            {sizePresets.map((preset) => (
              <button
                type="button"
                key={`${preset.label}-${preset.size}`}
                className={input.size === preset.size ? 'size-card active' : 'size-card'}
                onClick={() => onChange({ ...input, size: preset.size })}
              >
                <strong>{t(preset.label)}</strong>
                <span>{t(preset.description)}</span>
              </button>
            ))}
          </div>
          <label className="custom-size-input">
            <span>{t('form.exactSizeLabel')}</span>
            <input
              aria-label={t('form.roomSizeAriaLabel')}
              type="number"
              min="8"
              max="80"
              value={input.size}
              onChange={(event) => onChange({ ...input, size: Number(event.target.value || 0) })}
            />
          </label>
        </div>

        <div className="control-block">
          <span className="friendly-label">{t('form.furnishingLevelLabel')}</span>
          <div className="furnishing-level-grid" role="group" aria-label={t('form.furnishingLevelAriaLabel')}>
            {furnishingLevels.map((level) => (
              <button
                type="button"
                key={level.value}
                className={input.furnishingLevel === level.value ? 'level-card active' : 'level-card'}
                onClick={() => onChange({ ...input, furnishingLevel: level.value })}
              >
                <strong>{t(level.label)}</strong>
                <span>{t(level.description)}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="form-row compact-row">
          <label>
            <span>{t('form.styleLabel')}</span>
            <select value={input.style} onChange={(event) => onChange({ ...input, style: event.target.value as StyleType })}>
              {styles.map((style) => (
                <option key={style.value} value={style.value}>
                  {t(style.label)}
                </option>
              ))}
            </select>
            <small className="field-help">{t(styles.find((style) => style.value === input.style)?.hint ?? '')}</small>
          </label>
          <label>
            <span>{t('form.locationLabel')}</span>
            <input aria-label={t('form.locationAriaLabel')} value={input.location} onChange={(event) => onChange({ ...input, location: event.target.value })} />
            <small className="field-help">{t('form.locationHelp')}</small>
          </label>
        </div>
      </div>

      <div className="form-step shopping-step">
        <div className="step-kicker">{t('form.stepShoppingKicker')}</div>
        <h3>{t('form.shoppingHeading')}</h3>
        <p>{t('form.shoppingIntro')}</p>
        <div className="shop-mode-grid" role="group" aria-label={t('form.shopModeAriaLabel')}>
          <button
            type="button"
            className={shopMode === 'best-combo' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: retailers, optimizationGoal: 'best-value' })}
          >
            <strong>{t('form.shopBestComboTitle')}</strong>
            <span>{t('form.shopBestComboDescription')}</span>
          </button>
          <button
            type="button"
            className={shopMode === 'one-store' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'single', selectedRetailers: [input.selectedRetailers[0] ?? 'IKEA'], optimizationGoal: 'least-stores' })}
          >
            <strong>{t('form.shopOneStoreTitle')}</strong>
            <span>{t('form.shopOneStoreDescription')}</span>
          </button>
          <button
            type="button"
            className={shopMode === 'choose-stores' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: input.selectedRetailers.length ? input.selectedRetailers : ['IKEA', 'JYSK'] })}
          >
            <strong>{t('form.shopChooseStoresTitle')}</strong>
            <span>{t('form.shopChooseStoresDescription')}</span>
          </button>
        </div>

        <div className="retailer-pills friendly-pills">
          {retailers.map((retailer) => {
            const active = input.selectedRetailers.includes(retailer);
            return (
              <button type="button" key={retailer} className={active ? 'pill active' : 'pill'} onClick={() => onChange(setRetailer(input, retailer))}>
                {retailer}
              </button>
            );
          })}
        </div>
      </div>

      <div className="form-step">
        <div className="step-kicker">{t('form.stepWishesKicker')}</div>
        <h3>{t('form.wishesHeading')}</h3>
        <div className="field-group compact-group">
          <span className="friendly-label">{t('form.mustHaveLabel')}</span>
          <div className="category-pills friendly-pills">
            {visibleCategoryOptions.map((category) => (
              <button
                type="button"
                key={category}
                className={input.mustHaveCategories.includes(category) ? 'pill active' : 'pill'}
                onClick={() =>
                  onChange({
                    ...input,
                    mustHaveCategories: toggle(input.mustHaveCategories, category),
                    alreadyHaveCategories: input.alreadyHaveCategories.filter((item) => item !== category)
                  })
                }
              >
                {categoryLabels[category]}
              </button>
            ))}
          </div>
        </div>

        <div className="field-group compact-group already-have-group">
          <span className="friendly-label">{t('form.alreadyHaveLabel')}</span>
          <p className="field-help inline-help">{t('form.alreadyHaveHelp')}</p>
          <label className="already-have-text-input">
            <span>{t('form.alreadyHaveInputLabel')}</span>
            <input
              aria-label={t('form.alreadyHaveAriaLabel')}
              value={alreadyHaveFreeText}
              placeholder={t('form.alreadyHavePlaceholder')}
              onChange={(event) => {
                const value = event.target.value;
                setAlreadyHaveFreeText(value);
                const parsed = categoriesFromText(value);
                if (!parsed.length) return;
                onChange({
                  ...input,
                  alreadyHaveCategories: Array.from(new Set([...input.alreadyHaveCategories, ...parsed])),
                  mustHaveCategories: input.mustHaveCategories.filter((item) => !parsed.includes(item))
                });
              }}
            />
          </label>
          {input.alreadyHaveCategories.length > 0 && (
            <div className="existing-assets-note">
              <strong>{t('form.wontOfferAgain')}</strong> {alreadyHaveText}
            </div>
          )}
          <div className="category-pills muted-pills friendly-pills">
            {visibleCategoryOptions.map((category) => (
              <button
                type="button"
                key={category}
                className={input.alreadyHaveCategories.includes(category) ? 'pill active muted-active' : 'pill'}
                onClick={() =>
                  onChange({
                    ...input,
                    alreadyHaveCategories: toggle(input.alreadyHaveCategories, category),
                    mustHaveCategories: input.mustHaveCategories.filter((item) => item !== category)
                  })
                }
              >
                {categoryLabels[category]}
              </button>
            ))}
          </div>
        </div>

        <div className="field-group compact-group">
          <span className="friendly-label">{t('form.mostImportantLabel')}</span>
          <div className="optimization-grid friendly-goals">
            {optimizationGoals.map((goal) => (
              <button
                type="button"
                key={goal.value}
                className={input.optimizationGoal === goal.value ? 'goal-card active' : 'goal-card'}
                onClick={() => onChange({ ...input, optimizationGoal: goal.value })}
              >
                <strong>{t(goal.label)}</strong>
                <span>{t(goal.description)}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
      </details>

      <div className="sticky-generate-bar">
        <button className="generate-button" type="submit" disabled={isLoading}>
          {isLoading ? t('planner.generating') : t('planner.generate')}
          <span>{t('form.budgetBarHint', { amount: formatCurrency(input.budget) })}</span>
        </button>
      </div>
    </form>
  );
}
