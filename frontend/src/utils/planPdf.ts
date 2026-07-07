// Sprint 10.168: open the shopping list as a real PDF in a new browser tab (the browser's PDF viewer then
// offers its own print + download). pdfmake is lazy-loaded (only fetched on the first click, so it never
// weighs down initial load) and uses Roboto, which renders Croatian č/ć/š/ž/đ correctly.

export interface PdfSection {
  title: string;
  subtotal: number;
  items: Array<{ name: string; meta: string; lineTotal: number }>;
}

export interface PdfPlanArgs {
  title: string;
  subtitle?: string;
  budget: number;
  total: number;
  sections: PdfSection[];
  stores: Array<{ retailer: string; count: number; total: number }>;
  money: (value: number) => string;
  labels: {
    shoppingList: string;
    budget: string;
    total: string;
    remaining: string;
    over: string;
    byStore: string;
    disclaimer: string;
    madeWith: string;
    itemsCount: (count: number) => string;
  };
}

// Palette refresh: match the app's warm interior-planner tokens (was the retired orange/cool-grey set).
const INK = '#1D1A16';
const MUTED = '#70685E';
const ACCENT = '#8F4E30'; // readable clay for section heads (matches on-screen section labels)
const RULE = '#DDD1C2';

// pdfmake's getBlob is callback-based in the browser build; wrap it (and tolerate a promise form).
function toBlob(pdfDoc: { getBlob: (cb: (blob: Blob) => void) => unknown }): Promise<Blob> {
  return new Promise((resolve, reject) => {
    try {
      const maybe = pdfDoc.getBlob((blob: Blob) => resolve(blob));
      if (maybe && typeof (maybe as Promise<Blob>).then === 'function') {
        (maybe as Promise<Blob>).then(resolve, reject);
      }
    } catch (error) {
      reject(error);
    }
  });
}

export async function openPlanPdf(args: PdfPlanArgs): Promise<void> {
  // Reserve the tab synchronously inside the click gesture so the async PDF build isn't popup-blocked.
  const win = typeof window !== 'undefined' ? window.open('', '_blank') : null;
  try {
    const [pdfMakeMod, vfsMod] = await Promise.all([
      import('pdfmake/build/pdfmake'),
      import('pdfmake/build/vfs_fonts'),
    ]);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const pdfMake: any = (pdfMakeMod as any).default ?? pdfMakeMod;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const vfs: any = (vfsMod as any).default ?? vfsMod;
    if (typeof pdfMake.addVirtualFileSystem === 'function') pdfMake.addVirtualFileSystem(vfs);
    else pdfMake.vfs = vfs?.pdfMake?.vfs ?? vfs;

    const remaining = args.budget - args.total;
    const rule = (width: number, color: string) => ({
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 515, y2: 0, lineWidth: width, lineColor: color }],
      margin: [0, 2, 0, 6],
    });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const content: any[] = [
      { columns: [
        { text: 'budgetspace', style: 'brand' },
        { text: args.labels.shoppingList.toUpperCase(), style: 'kicker', alignment: 'right', margin: [0, 3, 0, 0] },
      ] },
      rule(1.5, INK),
      { text: args.title, style: 'title' },
    ];
    if (args.subtitle) content.push({ text: args.subtitle, style: 'subtitle' });
    content.push({
      columns: [
        { text: [{ text: args.labels.budget + ': ', color: MUTED }, { text: args.money(args.budget), bold: true }] },
        { text: [{ text: args.labels.total + ': ', color: MUTED }, { text: args.money(args.total), bold: true }] },
        { text: [{ text: (remaining >= 0 ? args.labels.remaining : args.labels.over) + ': ', color: MUTED }, { text: args.money(Math.abs(remaining)), bold: true }] },
      ],
      margin: [0, 10, 0, 6],
    });

    for (const section of args.sections) {
      content.push({
        columns: [
          { text: section.title.toUpperCase(), style: 'sectionHead' },
          { text: args.money(section.subtotal), style: 'sectionHead', alignment: 'right' },
        ],
        margin: [0, 10, 0, 0],
      });
      content.push(rule(0.5, RULE));
      content.push({
        table: {
          widths: ['*', 'auto', 'auto'],
          body: section.items.map((item) => [
            { text: item.name, style: 'itemName' },
            { text: item.meta, style: 'itemMeta' },
            { text: args.money(item.lineTotal), style: 'itemPrice', alignment: 'right' },
          ]),
        },
        layout: 'noBorders',
      });
    }

    if (args.stores.length) {
      content.push({ text: args.labels.byStore.toUpperCase(), style: 'sectionHead', margin: [0, 12, 0, 0] });
      content.push(rule(0.5, RULE));
      content.push({
        table: {
          widths: ['*', 'auto', 'auto'],
          body: args.stores.map((store) => [
            { text: store.retailer, style: 'itemName' },
            { text: args.labels.itemsCount(store.count), style: 'itemMeta' },
            { text: args.money(store.total), style: 'itemPrice', alignment: 'right' },
          ]),
        },
        layout: 'noBorders',
      });
    }

    content.push({ text: args.labels.disclaimer + ' · ' + args.labels.madeWith, style: 'foot', margin: [0, 20, 0, 0] });

    const docDefinition = {
      content,
      pageMargins: [40, 40, 40, 45],
      defaultStyle: { font: 'Roboto', fontSize: 10, color: INK },
      styles: {
        brand: { fontSize: 13, bold: true, color: INK },
        kicker: { fontSize: 8, color: MUTED },
        title: { fontSize: 20, bold: true, margin: [0, 6, 0, 0] },
        subtitle: { fontSize: 10, color: MUTED, margin: [0, 1, 0, 0] },
        sectionHead: { fontSize: 9, bold: true, color: ACCENT },
        itemName: { fontSize: 10, margin: [0, 1.5, 0, 1.5] },
        itemMeta: { fontSize: 8, color: MUTED, margin: [10, 1.5, 10, 1.5] },
        itemPrice: { fontSize: 10, margin: [0, 1.5, 0, 1.5] },
        foot: { fontSize: 7.5, color: MUTED },
      },
    };

    const blob = await toBlob(pdfMake.createPdf(docDefinition));
    const url = URL.createObjectURL(blob);
    if (win) win.location.href = url;
    else window.open(url, '_blank');
    // Give the tab time to load the blob before we release the object URL.
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error) {
    if (win) win.close();
    // Surfaced to the caller's console; the button simply does nothing if PDF generation fails.
    console.error('PDF export failed', error);
  }
}
