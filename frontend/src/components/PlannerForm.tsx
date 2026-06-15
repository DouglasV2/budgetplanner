import { useState } from 'react';
import type { FurnishingLevel, OptimizationGoal, PlannerInput, ProductCategory, Retailer, RoomType, StyleType } from '../types';
import { categoryLabels, formatCurrency, retailers } from '../utils/planner';

interface PlannerFormProps {
  input: PlannerInput;
  onChange: (input: PlannerInput) => void;
  onGenerate: () => void;
  isLoading?: boolean;
}

const rooms: Array<{ value: RoomType; label: string; icon: string; hint: string }> = [
  { value: 'living-room', label: 'Dnevni boravak', icon: '🛋️', hint: 'kauč, TV komoda, tepih' },
  { value: 'home-office', label: 'Radni kutak', icon: '💻', hint: 'stol, stolica, rasvjeta' },
  { value: 'bedroom', label: 'Spavaća soba', icon: '🛏️', hint: 'krevet, ormar, lampe' },
  { value: 'home-gym', label: 'Kućna teretana', icon: '🏋️', hint: 'oprema, spremanje, podloga' },
  { value: 'dining-room', label: 'Blagovaonica', icon: '🍽️', hint: 'stol, stolice, rasvjeta' },
  { value: 'kitchen', label: 'Kuhinja', icon: '🍳', hint: 'kolica, polica, rasvjeta' },
  { value: 'hallway', label: 'Hodnik', icon: '🚪', hint: 'ormar za cipele, polica' },
  { value: 'bathroom', label: 'Kupaonica', icon: '🛁', hint: 'ormarić, polica (uskoro)' }
];

const styles: Array<{ value: StyleType; label: string; hint: string }> = [
  { value: 'surprise', label: 'Nisam siguran, predloži mi', hint: 'najbolje ako ne znaš stilove' },
  { value: 'bright', label: 'Svijetlo i prozračno', hint: 'svijetle boje, lagan osjećaj' },
  { value: 'warm', label: 'Toplo i domaće', hint: 'ugodnije, mekše, više tekstura' },
  { value: 'modern', label: 'Moderno i uredno', hint: 'čiste linije, praktično' },
  { value: 'minimal', label: 'Jednostavno i čisto', hint: 'bez puno detalja' },
  { value: 'classic', label: 'Klasično', hint: 'sigurno i dugoročno' },
  { value: 'industrial', label: 'Tamno / industrijski', hint: 'crni metal, drvo, jači kontrast' },
  { value: 'boho', label: 'Boho / prirodno', hint: 'drvo, tepisi, biljke, opušteno' }
];

const furnishingLevels: Array<{ value: FurnishingLevel; label: string; description: string }> = [
  { value: 'basic', label: 'Osnovno', description: 'prvo najvažniji komadi' },
  { value: 'comfort', label: 'Udobnije', description: 'baza + stvari koje čine prostor ugodnijim' },
  { value: 'complete', label: 'Kompletno', description: 'dodaj i detalje ako budžet drži' }
];

const optimizationGoals: Array<{ value: OptimizationGoal; label: string; description: string }> = [
  { value: 'best-value', label: 'Najbolji omjer', description: 'dobar izgled bez bacanja novca' },
  { value: 'lowest-price', label: 'Što jeftinije', description: 'čuvaj budžet koliko god možeš' },
  { value: 'least-stores', label: 'Što manje trgovina', description: 'manje dostava i manje obilazaka' },
  { value: 'style-match', label: 'Što ljepše', description: 'prednost imaju skladniji proizvodi' }
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
  { label: 'Ne znam', size: 20, description: 'aplikacija uzme sigurnu procjenu' },
  { label: 'Mala', size: 12, description: 'garsonijera ili manji kutak' },
  { label: 'Srednja', size: 20, description: 'tipična soba u stanu' },
  { label: 'Velika', size: 32, description: 'veći dnevni boravak' }
];

const budgetPresets = [700, 1000, 1500, 2500];

const starterTemplates: Array<{ title: string; subtitle: string; input: Partial<PlannerInput> }> = [
  {
    title: 'Dnevni boravak do 1500 €',
    subtitle: 'kauč, TV komoda, tepih, stolić, rasvjeta',
    input: {
      prompt: 'Imam 1500 € za dnevni boravak u Zagrebu. Želim svijetli i prozračni stil, preferiram IKEA i JYSK. Trebam kauč, TV komodu, klub stolić, tepih i lampu.',
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
    title: 'Radni kutak do 800 €',
    subtitle: 'stol, stolica, polica i rasvjeta',
    input: {
      prompt: 'Imam 800 € za radni kutak. Želim jednostavan i uredan radni prostor, može IKEA, JYSK i Pevex. Trebam radni stol, stolicu, policu i lampu.',
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
    title: 'Kućna teretana do 1200 €',
    subtitle: 'Decathlon + Pevex oprema za početak',
    input: {
      prompt: 'Imam 1200 € za kućnu teretanu. Želim praktičan prostor za vježbanje, preferiram Decathlon i Pevex. Trebam osnovnu opremu, spremanje i rasvjetu.',
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
    title: 'Samo IKEA dnevni boravak',
    subtitle: 'jedna trgovina, manje logistike',
    input: {
      prompt: 'Imam 1800 € za dnevni boravak i želim sve iz IKEA-e. Želim svijetli, uredan stil. Već imam TV, trebam ostatak prostora.',
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

function applyTemplate(input: PlannerInput, template: Partial<PlannerInput>): PlannerInput {
  return {
    ...input,
    ...template,
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
        <div className="step-kicker">1. Opiši želju</div>
        <div className="prompt-topline">
          <div>
            <h3>Što želiš opremiti?</h3>
            <p>Napiši normalno, kao da šalješ poruku prijatelju. Ne moraš znati stručne izraze.</p>
          </div>
          <span className="ai-chip">Najlakše</span>
        </div>
        <label>
          <span>Ovdje napiši želju</span>
          <textarea
            aria-label="Opis prostora i želja"
            rows={7}
            value={input.prompt}
            placeholder="Npr. Imam 1500 € za dnevni boravak. Želim svijetli i prozračni stil, ne želim obilaziti puno trgovina i već imam TV."
            onChange={(event) => onChange({ ...input, prompt: event.target.value })}
          />
        </label>
        <button className="generate-button" type="submit" disabled={isLoading}>
          {isLoading ? 'Slažem plan...' : 'Složi moj plan'}
          <span>dobiješ popis proizvoda i ukupnu cijenu</span>
        </button>
      </div>

      <div className="form-step starter-template-panel">
        <div className="step-kicker">Brzi početak</div>
        <h3>Ne znaš što napisati?</h3>
        <p>Odaberi primjer pa ga po želji promijeni.</p>
        <div className="template-grid">
          {starterTemplates.map((template) => (
            <button type="button" className="template-card" key={template.title} onClick={() => onChange(applyTemplate(input, template.input))}>
              <strong>{template.title}</strong>
              <span>{template.subtitle}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="form-step easy-controls">
        <div className="step-kicker">2. Podesi ako želiš</div>
        <h3>Osnovne stvari</h3>
        <p>Ovo možeš preskočiti ako si sve napisao gore.</p>

        <div className="control-block budget-block">
          <span className="friendly-label">Koliko želiš potrošiti?</span>
          <label className="budget-input-wrap">
            <input
              aria-label="Koliko želiš potrošiti"
              type="number"
              min="100"
              step="50"
              value={input.budget}
              onChange={(event) => onChange({ ...input, budget: Number(event.target.value || 0) })}
            />
            <span>€</span>
          </label>
          <div className="budget-presets" aria-label="Brzi odabir budžeta">
            {budgetPresets.map((budget) => (
              <button type="button" key={budget} className={input.budget === budget ? 'preset active' : 'preset'} onClick={() => onChange({ ...input, budget })}>
                {formatCurrency(budget)}
              </button>
            ))}
          </div>
        </div>

        <div className="control-block">
          <span className="friendly-label">Što opremaš?</span>
          <div className="choice-grid rooms friendly-rooms">
            {rooms.map((room) => (
              <button
                type="button"
                className={input.roomType === room.value ? 'choice active' : 'choice'}
                key={room.value}
                onClick={() => onChange({ ...input, roomType: room.value })}
              >
                <span>{room.icon}</span>
                <strong>{room.label}</strong>
                <small>{room.hint}</small>
              </button>
            ))}
          </div>
        </div>

        <div className="control-block">
          <span className="friendly-label">Koliko je velika prostorija?</span>
          <div className="size-grid">
            {sizePresets.map((preset) => (
              <button
                type="button"
                key={`${preset.label}-${preset.size}`}
                className={input.size === preset.size ? 'size-card active' : 'size-card'}
                onClick={() => onChange({ ...input, size: preset.size })}
              >
                <strong>{preset.label}</strong>
                <span>{preset.description}</span>
              </button>
            ))}
          </div>
          <label className="custom-size-input">
            <span>Točno u m² ako znaš</span>
            <input
              aria-label="Veličina prostorije"
              type="number"
              min="8"
              max="80"
              value={input.size}
              onChange={(event) => onChange({ ...input, size: Number(event.target.value || 0) })}
            />
          </label>
        </div>

        <div className="control-block">
          <span className="friendly-label">Koliko kompletno želiš opremiti?</span>
          <div className="furnishing-level-grid" role="group" aria-label="Razina opremanja">
            {furnishingLevels.map((level) => (
              <button
                type="button"
                key={level.value}
                className={input.furnishingLevel === level.value ? 'level-card active' : 'level-card'}
                onClick={() => onChange({ ...input, furnishingLevel: level.value })}
              >
                <strong>{level.label}</strong>
                <span>{level.description}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="form-row compact-row">
          <label>
            <span>Kakav izgled želiš?</span>
            <select value={input.style} onChange={(event) => onChange({ ...input, style: event.target.value as StyleType })}>
              {styles.map((style) => (
                <option key={style.value} value={style.value}>
                  {style.label}
                </option>
              ))}
            </select>
            <small className="field-help">{styles.find((style) => style.value === input.style)?.hint}</small>
          </label>
          <label>
            <span>Grad ili država</span>
            <input aria-label="Lokacija" value={input.location} onChange={(event) => onChange({ ...input, location: event.target.value })} />
            <small className="field-help">Koristimo ovo kasnije za dostupnost i trgovine u blizini.</small>
          </label>
        </div>
      </div>

      <div className="form-step shopping-step">
        <div className="step-kicker">3. Kupnja</div>
        <h3>Gdje želiš kupovati?</h3>
        <p>Najjednostavnije je pustiti aplikaciji da kombinira, ali možeš ograničiti trgovine.</p>
        <div className="shop-mode-grid" role="group" aria-label="Odabir trgovina">
          <button
            type="button"
            className={shopMode === 'best-combo' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: retailers, optimizationGoal: 'best-value' })}
          >
            <strong>Svejedno mi je</strong>
            <span>Složi najbolju kombinaciju iz svih trgovina.</span>
          </button>
          <button
            type="button"
            className={shopMode === 'one-store' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'single', selectedRetailers: [input.selectedRetailers[0] ?? 'IKEA'], optimizationGoal: 'least-stores' })}
          >
            <strong>Jedna trgovina</strong>
            <span>Manje dostava, manje obilazaka.</span>
          </button>
          <button
            type="button"
            className={shopMode === 'choose-stores' ? 'shop-mode active' : 'shop-mode'}
            onClick={() => onChange({ ...input, retailerMode: 'multi', selectedRetailers: input.selectedRetailers.length ? input.selectedRetailers : ['IKEA', 'JYSK'] })}
          >
            <strong>Sam odabirem</strong>
            <span>Odaberi trgovine koje ti odgovaraju.</span>
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
        <div className="step-kicker">4. Želje</div>
        <h3>Što je bitno?</h3>
        <div className="field-group compact-group">
          <span className="friendly-label">Što obavezno trebaš?</span>
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
          <span className="friendly-label">Što već imaš?</span>
          <p className="field-help inline-help">Označi samo ako stvarno ne želiš da ti to ponovno nudimo.</p>
          <label className="already-have-text-input">
            <span>Upiši ako je lakše</span>
            <input
              aria-label="Što već imaš"
              value={alreadyHaveFreeText}
              placeholder="npr. kauč, TV, radni stol"
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
              <strong>Nećemo ponovno nuditi:</strong> {alreadyHaveText}
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
          <span className="friendly-label">Što ti je najvažnije?</span>
          <div className="optimization-grid friendly-goals">
            {optimizationGoals.map((goal) => (
              <button
                type="button"
                key={goal.value}
                className={input.optimizationGoal === goal.value ? 'goal-card active' : 'goal-card'}
                onClick={() => onChange({ ...input, optimizationGoal: goal.value })}
              >
                <strong>{goal.label}</strong>
                <span>{goal.description}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="sticky-generate-bar">
        <button className="generate-button" type="submit" disabled={isLoading}>
          {isLoading ? 'Slažem plan...' : 'Složi moj plan'}
          <span>{formatCurrency(input.budget)} budžet</span>
        </button>
      </div>
    </form>
  );
}
