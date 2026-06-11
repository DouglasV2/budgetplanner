from __future__ import annotations

import argparse
import json

from retailers.ikea import IkeaScraper
from retailers.jysk import JyskScraper
from retailers.pevex import PevexScraper

SCRAPERS = {
    "ikea": IkeaScraper,
    "jysk": JyskScraper,
    "pevex": PevexScraper,
}


def main() -> None:
    parser = argparse.ArgumentParser(description="BudgetSpace AI scraper runner")
    parser.add_argument("--retailer", choices=SCRAPERS.keys(), required=True)
    parser.add_argument("--limit", type=int, default=50)
    args = parser.parse_args()

    scraper = SCRAPERS[args.retailer]()
    products = [product.to_dict() for product in scraper.fetch(limit=args.limit)]
    print(json.dumps(products, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
