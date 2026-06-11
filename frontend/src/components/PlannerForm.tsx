import type { OptimizationGoal, PlannerInput, ProductCategory, Retailer, RoomType, StyleType } from '../types';
import { categoryLabels, formatCurrency, retailers } from '../utils/planner';

interface PlannerFormProps {
  input: PlannerInput;
  onChange: (input: PlannerInput) => void;
  onGenerate: () => void;
  isLoading?: boolean;
}

const rooms: Array<{ value: RoomType; label: string; icon: string }> = [
  { value: 'living-room', label: 'Dnevni boravak', icon: '🛋️' },
  { value: 'home-office', label: 'Radni kutak', icon: '💻' },
  { value: 'bedroom', label: 'Spavaća soba', icon: '🛏️' },
  { value: 'home-gym', label: 'Kućna teretana', icon: '🏋️' }
];

const styles: Array<{ value: StyleType; label: string }> = [
  { value: 'scandinavian', label: 'Skandinavski' },
  { value: 'modern', label: 'Moderni' },
  { value: 'minimal', label: 'Minimalistički' },
  { value: 'cozy', label: 'Toplo i ugodno' },
  { value: 'industrial', label: 'Industrijski' }
];

const optimizationGoals: Array<{ value: OptimizationGoal; label: string; description: string }> = [
  { value: 'best-value', label: 'Najbolji izbor', description: 'dobar balans cijene, izgleda i ocjene' },
  { value: 'lowest-price', label: 'Jeftinije', description: 'maksimalno čuva budžet' },
  { value: 'least-stores', label: 'Manje trgovina', description: 'manje dostava i odlazaka' },
  { value: 'style-match', label: 'Ljepši stil', description: 'prednost imaju skladniji proizvodi' }
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

const sizePresets = [
  { label: 'Ne znam', size: 20, description: 'uzmi sigurni default' },
  { label: 'Mali', size: 12, description: 'do oko 15 m²' },
  { label: 'Srednji', size: 20, description: '16–25 m²' },
  { label: 'Veliki', size: 32, description: 'više od 25 m²' }
];

const starterTemplates: Array<{ title: string; subtitle: string; input: Partial<PlannerInput> }> = [
  {
    title: 'Dnevni boravak do 1500 €',
    subtitle: 'kauč, TV komoda, tepih, stolić, rasvjeta',
    input: {
      prompt: 'Imam 1500 € za dnevni boravak u Zagrebu. Želim skandinavski stil, preferiram IKEA i JYSK. Trebam kauč, TV komodu, klub stolić, tepih i lampu.',
      budget: 1500,
      roomType: 'living-room',
      style: 'scandinavian',
      size: 20,
      selectedRetailers: ['IKEA', 'JYSK'],
      retailerMode: 'multi',
      mustHaveCategories: ['sofa', 'tv-unit', 'table', 'rug', 'lighting'],
      alreadyHaveCategories: []
    }
  },
  {
    title: 'Radni kutak do 800 €',
    subtitle: 'stol, stolica, polica i rasvjeta',
    input: {
      prompt: 'Imam 800 € za radni kutak. Želim moderan minimalistički radni prostor, može IKEA, JYSK i Pevex. Trebam radni stol, stolicu, policu i lampu.',
      budget: 800,
      roomType: 'home-office',
      style: 'minimal',
      size: 12,
      selectedRetailers: ['IKEA', 'JYSK', 'Pevex'],
      retailerMode: 'multi',
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
      mustHaveCategories: ['gym-equipment', 'storage', 'lighting'],
      alreadyHaveCategories: []
    }
  },
  {
    title: 'Samo IKEA dnevni boravak',
    subtitle: 'jedna trgovina, manje logistike',
    input: {
      prompt: 'Imam 1800 € za dnevni boravak i želim sve iz IKEA-e. Želim moderni skandinavski stil. Već imam TV, trebam ostatak prostora.',
      budget: 1800,
      roomType: 'living-room',
      style: 'scandinavian',
      size: 22,
      selectedRetailers: ['IKEA'],
      retailerMode: 'single',
      optimizationGoal: 'least-stores',
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

export function PlannerForm({ input, onChange, onGenerate, isLoading = false }: PlannerFormProps) {
  return (
    <form
      className="planner-form"
      onSubmit={(event) => {
        event.preventDefault();
        onGenerate();
      }}
    >
      <div className="prompt-card prompt-first-card">
        <div className="prompt-topline">
          <div>
            <span className="field-label">Tvoj opis</span>
            <h3>Napiši što želiš, bez razmišljanja o filterima.</h3>
          </div>
          <span className="ai-chip">Najbrži način</span>
        </div>
        <label>
          <span>Opiši što želiš</span>
          <textarea
            aria-label="Opis prostora i želja"
            rows={7}
            value={input.prompt}
            placeholder="Npr. Imam 1500 € za dnevni boravak. Želim skandinavski stil, preferiram IKEA i JYSK. Već imam TV, treba mi kauč, tepih, lampa i TV komoda."
            onChange={(event) => onChange({ ...input, prompt: event.target.value })}
          />
        </label>
        <button className="generate-button" type="submit" disabled={isLoading}>
          {isLoading ? 'Generiram plan...' : 'Generiraj plan'}
          <span>dobiješ konkretan popis za kupnju</span>
        </button>
        <p className="microcopy">Kontrole ispod su samo pomoć. Ako u opisu napišeš budžet, trgovine ili “već imam TV”, aplikacija će to pokušati sama prepoznati.</p>
      </div>

      <div className="starter-template-panel">
        <div className="field-label">Kreni od primjera</div>
        <div className="template-grid">
          {starterTemplates.map((template) => (
            <button type="button" className="template-card" key={template.title} onClick={() => onChange(applyTemplate(input, template.input))}>
              <strong>{template.title}</strong>
              <span>{template.subtitle}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="smart-controls">
        <div className="control-block budget-block">
          <span className="field-label">Budžet</span>
          <label className="budget-input-wrap">
            <input
              aria-label="Budget"
              type="number"
              min="100"
              step="50"
              value={input.budget}
              onChange={(event) => onChange({ ...input, budget: Number(event.target.value || 0) })}
            />
            <span>€</span>
          </label>
          <small>{formatCurrency(input.budget)} ukupni limit</small>
        </div>

        <div className="control-block">
          <span className="field-label">Veličina prostora</span>
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
            <span>Točno m²</span>
            <input
              aria-label="Room size"
              type="number"
              min="8"
              max="80"
              value={input.size}
              onChange={(event) => onChange({ ...input, size: Number(event.target.value || 0) })}
            />
          </label>
        </div>
      </div>

      <div className="field-group compact-group">
        <span className="field-label">Prostorija</span>
        <div className="choice-grid rooms">
          {rooms.map((room) => (
            <button
              type="button"
              className={input.roomType === room.value ? 'choice active' : 'choice'}
              key={room.value}
              onClick={() => onChange({ ...input, roomType: room.value })}
            >
              <span>{room.icon}</span>
              {room.label}
            </button>
          ))}
        </div>
      </div>

      <div className="form-row compact-row">
        <label>
          <span>Stil</span>
          <select value={input.style} onChange={(event) => onChange({ ...input, style: event.target.value as StyleType })}>
            {styles.map((style) => (
              <option key={style.value} value={style.value}>
                {style.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Lokacija</span>
          <input value={input.location} onChange={(event) => onChange({ ...input, location: event.target.value })} />
        </label>
      </div>

      <div className="field-group compact-group">
        <span className="field-label">Trgovine</span>
        <div className="segmented-control">
          <button
            type="button"
            className={input.retailerMode === 'single' ? 'segment active' : 'segment'}
            onClick={() => onChange({ ...input, retailerMode: 'single', selectedRetailers: [input.selectedRetailers[0] ?? 'IKEA'] })}
          >
            Jedna trgovina
          </button>
          <button
            type="button"
            className={input.retailerMode === 'multi' ? 'segment active' : 'segment'}
            onClick={() => onChange({ ...input, retailerMode: 'multi' })}
          >
            Kombiniraj
          </button>
        </div>
        <div className="retailer-pills">
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

      <div className="field-group compact-group">
        <span className="field-label">Prioritet</span>
        <div className="optimization-grid">
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

      <div className="field-group compact-group">
        <span className="field-label">Što obavezno trebaš?</span>
        <div className="category-pills">
          {categoryOrder.map((category) => (
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

      <div className="field-group compact-group">
        <span className="field-label">Što već imaš?</span>
        <div className="category-pills muted-pills">
          {categoryOrder.map((category) => (
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
    </form>
  );
}
