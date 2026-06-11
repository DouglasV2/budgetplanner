from base_scraper import RetailerScraper


class PevexScraper(RetailerScraper):
    retailer_name = "Pevex"

    def fetch(self, limit: int = 50):
        return []
