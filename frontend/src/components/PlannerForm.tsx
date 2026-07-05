import { useState } from 'react';
import type { FurnishingLevel, OptimizationGoal, PlannerInput, ProductCategory, Retailer, RoomType, StyleType } from '../types';
import { categoryLabels, formatCurrency, retailersForMarket } from '../utils/planner';
import { MARKETS, citiesForMarket } from '../markets';
import { useLocale } from '../LocaleContext';
import { RoomIcon } from './icons';

interface PlannerFormProps {
  input: PlannerInput;
  onChange: (input: PlannerInput) => void;
  onGenerate: () => void;
  isLoading?: boolean;
}

const rooms: Array<{ value: RoomType; label: string; hint: string }> = [
  { value: 'living-room', label: 'form.roomLivingRoomLabel', hint: 'form.roomLivingRoomHint' },
  { value: 'home-office', label: 'form.roomHomeOfficeLabel', hint: 'form.roomHomeOfficeHint' },
  { value: 'bedroom', label: 'form.roomBedroomLabel', hint: 'form.roomBedroomHint' },
  { value: 'studio', label: 'form.roomStudioLabel', hint: 'form.roomStudioHint' },
  // Sprint 10.79: home-gym de-scoped — no verified gym-equipment products (IKEA's DAJLIEN range is
  // discontinued; Decathlon is feed-blocked), so the room always came back empty. Removed from the picker.
  // The room type + backend maps stay (dormant) so it can be re-added once a sports-retailer feed exists.
  { value: 'dining-room', label: 'form.roomDiningRoomLabel', hint: 'form.roomDiningRoomHint' },
  { value: 'kitchen', label: 'form.roomKitchenLabel', hint: 'form.roomKitchenHint' },
  { value: 'hallway', label: 'form.roomHallwayLabel', hint: 'form.roomHallwayHint' },
  { value: 'bathroom', label: 'form.roomBathroomLabel', hint: 'form.roomBathroomHint' }
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

// Sprint 10.57: "Choose a vibe" — a premium one-tap style picker for people who can't describe their style.
// Each vibe maps to the EXISTING engine (a canonical style + colour/material preferences the planner already
// scores on) and sets a STYLE-PURE prompt — verified live so the rule-based extractor yields exactly this
// style and preserves the vibe's colours/materials (the prompt carries no room/colour/material/budget words).
type Vibe = {
  id: string;
  label: string;
  desc: string;
  promptKey: string;
  style: StyleType;
  colors: string[];
  materials: string[];
  swatch: [string, string, string];
};

const vibes: Vibe[] = [
  { id: 'scandinavian', label: 'form.vibeScandinavianLabel', desc: 'form.vibeScandinavianDesc', promptKey: 'form.vibeScandinavianPrompt', style: 'bright', colors: ['white', 'grey', 'natural'], materials: ['wood', 'fabric'], swatch: ['#EFEADF', '#CFC7B7', '#A98E68'] },
  { id: 'japandi', label: 'form.vibeJapandiLabel', desc: 'form.vibeJapandiDesc', promptKey: 'form.vibeJapandiPrompt', style: 'minimal', colors: ['beige', 'natural', 'black'], materials: ['wood', 'rattan'], swatch: ['#E6DCC9', '#B79A6F', '#2C2A26'] },
  { id: 'minimalist', label: 'form.vibeMinimalistLabel', desc: 'form.vibeMinimalistDesc', promptKey: 'form.vibeMinimalistPrompt', style: 'minimal', colors: ['white', 'grey'], materials: ['metal', 'glass'], swatch: ['#FBFBFA', '#D4D4D2', '#2B2B2B'] },
  { id: 'industrial', label: 'form.vibeIndustrialLabel', desc: 'form.vibeIndustrialDesc', promptKey: 'form.vibeIndustrialPrompt', style: 'industrial', colors: ['black', 'grey', 'brown'], materials: ['metal', 'wood', 'leather'], swatch: ['#3A3936', '#7C786F', '#5A4332'] },
  { id: 'warm-modern', label: 'form.vibeWarmModernLabel', desc: 'form.vibeWarmModernDesc', promptKey: 'form.vibeWarmModernPrompt', style: 'warm', colors: ['beige', 'brown', 'natural'], materials: ['wood', 'fabric', 'velvet'], swatch: ['#E4C9A6', '#B07C4F', '#6E4A2D'] },
  { id: 'luxury-hotel', label: 'form.vibeLuxuryLabel', desc: 'form.vibeLuxuryDesc', promptKey: 'form.vibeLuxuryPrompt', style: 'classic', colors: ['black', 'grey', 'gold'], materials: ['velvet', 'marble', 'leather'], swatch: ['#1E1A15', '#C2A86B', '#6F6F6F'] }
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
  'living-room': ['sofa', 'tv-unit', 'table', 'rug', 'lighting', 'storage', 'textiles', 'decor'],
  'home-office': ['desk', 'chair', 'storage', 'lighting', 'decor'],
  bedroom: ['bed', 'mattress', 'nightstand', 'wardrobe', 'dresser', 'storage', 'lighting', 'rug', 'textiles', 'decor'],
  'home-gym': ['gym-equipment', 'storage', 'lighting', 'decor'],
  kitchen: ['kitchen-cart', 'kitchen-storage', 'lighting', 'storage', 'decor'],
  'dining-room': ['dining-table', 'dining-chair', 'lighting', 'rug', 'storage', 'decor'],
  hallway: ['storage', 'lighting', 'rug', 'decor'],
  bathroom: ['storage', 'lighting', 'decor'],
  studio: ['bed', 'mattress', 'sofa', 'dining-table', 'wardrobe', 'table', 'storage', 'lighting', 'tv-unit', 'rug', 'nightstand', 'textiles', 'decor']
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

// Sprint 10.57: apply a vibe as a style overlay — keeps the room/budget/size/stores, sets the style +
// colour/material preferences, and a style-pure prompt the extractor maps to this exact style.
function applyVibe(input: PlannerInput, vibe: Vibe, prompt: string): PlannerInput {
  return {
    ...input,
    style: vibe.style,
    colorPreferences: vibe.colors,
    materialPreferences: vibe.materials,
    prompt,
    lockedProductIds: []
  };
}

function selectedShopMode(input: PlannerInput, marketRetailers: Retailer[]) {
  if (input.retailerMode === 'single') return 'one-store';
  if (marketRetailers.length > 0 && marketRetailers.every((retailer) => input.selectedRetailers.includes(retailer))) return 'best-combo';
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

// Sprint 10.168: a simple, non-branded checklist glyph for the primary "Složi plan" button.
function PlanIcon() {
  return (
    <svg className="generate-button-icon" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 6.4l1.4 1.4L8 5.4" />
      <path d="M11 7h9" />
      <path d="M4 12.5h.01" />
      <path d="M11 12.5h9" />
      <path d="M4 18.5h.01" />
      <path d="M11 18.5h9" />
    </svg>
  );
}

export function PlannerForm({ input, onChange, onGenerate, isLoading = false }: PlannerFormProps) {
  const { t, market, setMarket } = useLocale();
  const [alreadyHaveFreeText, setAlreadyHaveFreeText] = useState('');
  const [selectedVibe, setSelectedVibe] = useState<string | null>(null);
  const marketRetailers = retailersForMarket(market);
  const shopMode = selectedShopMode(input, marketRetailers);
  const cityExample = citiesForMarket(market)[0] ?? '';
  const visibleCategoryOptions = visibleCategories(input);
  const alreadyHaveText = input.alreadyHaveCategories.map((category) => categoryLabels[category]).join(", ");

  return (
    <form
      className="planner-form friendly-form"
      // Sprint 10.168: noValidate — a number input with an out-of-range/step-mismatched value that sits
      // inside a collapsed <details> can't be focused, so the browser silently BLOCKS submit ("An invalid
      // form control … is not focusable") and the plan never generates. We validate/clamp in JS + backend.
      noValidate
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
        {/* Sprint 10.163 (EU AI Act Art.50): a point-of-interaction notice that an AI processes the typed text. */}
        <small className="field-help ai-interaction-notice">{t('planner.aiInteractionNotice')}</small>
        <button className="generate-button" type="submit" disabled={isLoading}>
          <span className="generate-button-label"><PlanIcon />{isLoading ? t('planner.generating') : t('planner.generate')}</span>
          <span className="generate-button-hint">{t('form.generateHint')}</span>
        </button>
      </div>

      <div className="form-step vibe-panel">
        <div className="step-kicker">{t('form.vibeKicker')}</div>
        <h3>{t('form.vibeHeading')}</h3>
        <p>{t('form.vibeIntro')}</p>
        <div className="vibe-grid" role="group" aria-label={t('form.vibeHeading')}>
          {vibes.map((vibe) => (
            <button
              type="button"
              key={vibe.id}
              className={selectedVibe === vibe.id ? 'vibe-card active' : 'vibe-card'}
              aria-pressed={selectedVibe === vibe.id}
              onClick={() => {
                setSelectedVibe(vibe.id);
                onChange(applyVibe(input, vibe, t(vibe.promptKey)));
              }}
            >
              <span className="vibe-swatch" aria-hidden="true">
                {vibe.swatch.map((colour, index) => (
                  <span key={index} style={{ background: colour }} />
                ))}
              </span>
              <strong>{t(vibe.label)}</strong>
              <small>{t(vibe.desc)}</small>
            </button>
          ))}
        </div>
        {/* Sprint 10.58: moodboard upload — honest placeholder (needs a vision/AI layer). No fake upload. */}
        <button type="button" className="moodboard-placeholder" disabled title={t('form.moodboardTooltip')}>
          <span className="moodboard-icon" aria-hidden="true">🖼️</span>
          <span className="moodboard-text">
            <strong>{t('form.moodboardTitle')}</strong>
            <small>{t('form.moodboardCta')}</small>
          </span>
          <span className="soon-badge">{t('form.moodboardSoon')}</span>
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
              inputMode="numeric"
              value={input.budget || ''}
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
                <span className="choice-icon"><RoomIcon room={room.value} /></span>
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
            <span className="input-with-unit">
              <input
                aria-label={t('form.roomSizeAriaLabel')}
                type="number"
                min="8"
                max="80"
                value={input.size || ''}
                onChange={(event) => onChange({ ...input, size: Number(event.target.value || 0) })}
              />
              <span className="input-unit" aria-hidden="true">m²</span>
            </span>
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
            <span>{t('form.countryLabel')}</span>
            <select
              className="country-select"
              value={market}
              aria-label={t('form.countryLabel')}
              onChange={(event) => setMarket(event.target.value)}
            >
              {MARKETS.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.flag} {option.label}
                </option>
              ))}
            </select>
            <small className="field-help">{t('form.countryHelp')}</small>
          </label>
        </div>

        <div className="control-block city-block">
          <span className="friendly-label">{t('form.cityLabel')}</span>
          <input
            className="city-input"
            list="city-suggestions"
            aria-label={t('form.cityAriaLabel')}
            value={input.location}
            placeholder={t('form.cityPlaceholder', { example: cityExample })}
            onChange={(event) => onChange({ ...input, location: event.target.value })}
          />
          <datalist id="city-suggestions">
            {citiesForMarket(market).map((city) => (
              <option key={city} value={city} />
            ))}
          </datalist>
          <small className="field-help">{t('form.locationHelp')}</small>
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
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: marketRetailers, optimizationGoal: 'best-value' })}
          >
            <strong>{t('form.shopBestComboTitle')}</strong>
            <span>{t('form.shopBestComboDescription')}</span>
          </button>
          <button
            type="button"
            className={shopMode === 'one-store' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'single', selectedRetailers: [marketRetailers.find((retailer) => input.selectedRetailers.includes(retailer)) ?? marketRetailers[0] ?? 'IKEA'], optimizationGoal: 'least-stores' })}
          >
            <strong>{t('form.shopOneStoreTitle')}</strong>
            <span>{t('form.shopOneStoreDescription')}</span>
          </button>
          <button
            type="button"
            className={shopMode === 'choose-stores' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: input.selectedRetailers.length ? input.selectedRetailers : marketRetailers.slice(0, 2) })}
          >
            <strong>{t('form.shopChooseStoresTitle')}</strong>
            <span>{t('form.shopChooseStoresDescription')}</span>
          </button>
        </div>

        <div className="retailer-pills friendly-pills">
          {marketRetailers.map((retailer) => {
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
          <span className="generate-button-label"><PlanIcon />{isLoading ? t('planner.generating') : t('planner.generate')}</span>
          <span className="generate-button-hint">{t('form.budgetBarHint', { amount: formatCurrency(input.budget) })}</span>
        </button>
      </div>
    </form>
  );
}
