# Planner intent — primjeri i očekivano ponašanje

Ovo je ručni test za parsiranje korisničke rečenice (bez LLM-a). Logika je u
`PlannerIntentExtractor` (backend), a automatski testovi su u
`PlannerIntentExtractorTest` i `PlannerServiceTest`.

Glavno pravilo: **"Imam 1500 €" je budžet, a ne stvar koju korisnik već ima.**
Kategoriju izbacuju samo fraze tipa "već imam", "imam …" (uz proizvod, ne broj),
"ne treba mi", "bez …", "ne dodaj …". Kategoriju traže fraze tipa "treba mi …",
"obavezno …", "najvažniji mi je …".

Kako provjeriti ručno: pokreni backend i frontend, upiši prompt u planer i pogledaj
"Preporučena kombinacija" iznad popisa te koji su proizvodi ušli.

| # | Prompt | Očekivano |
| --- | --- | --- |
| 1 | „Imam 1500 € za dnevni boravak, moderno, ne želim puno trgovina.” | Budžet 1500, dnevni boravak, stil moderno. Plan cilja na manje trgovina (najviše 2) i kaže „Većinu kupuješ u …”. Ništa se ne izbacuje kao „već imam”. |
| 2 | „Imam 800 € za radni kutak, već imam stolicu, treba mi stol i lampa.” | Budžet 800, radni kutak. Stolica (chair) se ne dodaje. Lampa (lighting) je tražena. „stol” ostaje neodređen i nikad ne postaje stolica; radni stol je ionako najvažniji za taj prostor. |
| 3 | „Spavaća soba do 1200 €, bez dekoracija, najvažniji su krevet i madrac.” | Budžet 1200, spavaća soba. Dekoracije (decor) se ne dodaju. Krevet i madrac su prioritet i ostaju u planu. |
| 4 | „Kućna teretana do 500 €, samo osnovno.” | Budžet 500, kućna teretana, razina „osnovno”. Plan drži samo najvažnije (oprema za vježbanje), bez puno dodataka. |
| 5 | „Dnevni boravak, imam TV i tepih, treba mi kauč i TV komoda.” | TV i tepih se ne nude ponovno. Kauč (sofa) je tražen. „TV komoda” (tv-unit) je izričito tražena, pa ulazi iako je rečeno „imam TV”. |
| 6 | „Želim ljepšu verziju spavaće sobe, ali ne preko 1500 €.” | Spavaća soba, budžet 1500, prednost ljepšim/skladnijim proizvodima, ali plan i dalje pazi na budžet i nudi popravak ako probije. |
| 7 | „Dnevni boravak do 900 €, jedna trgovina ako može.” | Budžet 900, dnevni boravak. Plan pokušava sve složiti u jednu trgovinu. Ako jedna trgovina nije dovoljna, dopušta drugu i to jasno kaže. |
| 8 | „Radni kutak do 600 €, bez Lesnine, najviše IKEA.” | Budžet 600, radni kutak. Lesnina se izbjegava. IKEA dobiva prednost kad su proizvodi dovoljno dobri. |

## Precizne kategorije

| Korisnik kaže | Kategorija |
| --- | --- |
| kauč, kauc, sofa, trosjed, dvosjed | sofa |
| TV komoda, komoda za TV, TV | tv-unit |
| stolić (klub stolić) | table |
| stolica | chair |
| radni stol | desk |
| tepih | rug |
| lampa, rasvjeta | lighting |
| polica, ormar, regal, komoda (bez „TV”) | storage |
| dekoracije, jastuk, slika, biljka | decor |
| krevet | bed |
| madrac | mattress |
| bučice, klupa, sprava, oprema za vježbanje | gym-equipment |

Napomene:
- „stolica” nikad ne postaje „stolić” i obrnuto.
- „stol” sam za sebe ostaje neodređen (nikad „stolica”). „radni stol” je desk.
- „komoda” je spremanje, osim u „TV komoda” gdje je tv-unit.

## Trgovine i broj odlazaka

- „najviše IKEA”, „radije JYSK”, „ako može Pevex” → ta trgovina dobiva prednost.
- „bez Lesnine”, „ne želim Decathlon”, „izbjegni Emmezetu” → ta trgovina se izbjegava.
- „jedna trgovina”, „sve iz jedne” → cilj je 1 trgovina.
- „maksimalno dvije trgovine”, „ne više od dvije trgovine” → cilj su 2 trgovine.
- „ne želim puno trgovina”, „bez puno obilazaka” → meka želja za manje trgovina (do 2).
