from base_scraper import RetailerScraper


class JyskScraper(RetailerScraper):
    retailer_name = "JYSK"

    def fetch(self, limit: int = 50):
        return []
