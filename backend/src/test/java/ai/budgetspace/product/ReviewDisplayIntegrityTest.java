package ai.budgetspace.product;

import ai.budgetspace.dto.ProductDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.14 — review-display integrity. We never fabricate a rating: the verified review aggregate
 * ({@code reviewRating}/{@code reviewCount}) is shown only when it actually exists, and it must never
 * be derived from the planner's internal heuristic {@code rating}. This pins the data-layer invariant
 * the UI relies on (the frontend only renders a review chip when {@code reviewRating}/{@code reviewCount}
 * are present).
 */
class ReviewDisplayIntegrityTest {

    @Test
    void productWithoutReviewsExposesNoFabricatedRating() {
        Product product = base();
        product.setRating(4.8); // planner's internal heuristic — must NOT surface as a review rating
        product.setReviewRating(null);
        product.setReviewCount(null);

        ProductDto dto = ProductDto.from(product);

        assertThat(dto.reviewRating()).as("no fabricated star rating").isNull();
        assertThat(dto.reviewCount()).as("no fabricated review count").isNull();
        // The planner rating is carried separately and is never the review rating.
        assertThat(dto.rating()).isEqualTo(4.8);
    }

    @Test
    void verifiedReviewAggregateIsPassedThroughUntouched() {
        Product product = base();
        product.setReviewRating(4.5);
        product.setReviewCount(305);
        product.setProductUrl("https://www.ikea.com/hr/hr/p/lack-12345678/");

        ProductDto dto = ProductDto.from(product);

        assertThat(dto.reviewRating()).isEqualTo(4.5);
        assertThat(dto.reviewCount()).isEqualTo(305);
        // reviewsUrl falls back to the real product page so "read reviews" always links somewhere real.
        assertThat(dto.reviewsUrl()).isEqualTo("https://www.ikea.com/hr/hr/p/lack-12345678/");
    }

    private Product base() {
        Product product = new Product();
        product.setId("review-test");
        product.setName("LACK polica");
        product.setRetailer("IKEA");
        product.setCategory("storage");
        product.setPrice(BigDecimal.valueOf(49.99));
        product.setStyleTags("minimal");
        product.setRoomTags("living-room");
        product.setInStock(true);
        return product;
    }
}
