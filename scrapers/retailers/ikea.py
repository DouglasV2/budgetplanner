from base_scraper import ProductRecord, RetailerScraper


class IkeaScraper(RetailerScraper):
    retailer_name = "IKEA"

    def fetch(self, limit: int = 50):
        # TODO: Implement carefully after checking IKEA terms/robots and preferred APIs/feeds.
        # Return ProductRecord objects normalized to the backend Product model.
        return []
