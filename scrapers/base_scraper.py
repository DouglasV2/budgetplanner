from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Iterable, Optional


@dataclass
class ProductRecord:
    source_id: str
    name: str
    retailer: str
    category: str
    price: float
    original_price: Optional[float]
    style_tags: list[str]
    room_tags: list[str]
    image: str
    url: str
    rating: float
    in_stock: bool
    note: str

    def to_dict(self) -> dict:
        return asdict(self)


class RetailerScraper:
    retailer_name: str

    def fetch(self, limit: int = 50) -> Iterable[ProductRecord]:
        raise NotImplementedError
