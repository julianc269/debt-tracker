package seedu.address.model.transaction;

import java.util.List;
import java.util.function.Predicate;

import seedu.address.commons.util.StringUtil;

/**
 * Tests that a {@code Transaction}'s {@code Date} matches any of the keywords given.
 */
public class NameContainsKeywordsPredicate implements Predicate<Transaction> {
    private final List<String> keywords;

    public NameContainsKeywordsPredicate(List<String> keywords) {
        this.keywords = keywords;
    }

    @Override
    public boolean test(Transaction Transaction) {
        return keywords.stream()
                .anyMatch(keyword -> StringUtil.containsWordIgnoreCase(Transaction.getType().toString(), keyword));
    }

    @Override
    public boolean equals(Object other) {
        return other == this // short circuit if same object
                || (other instanceof NameContainsKeywordsPredicate // instanceof handles nulls
                && keywords.equals(((NameContainsKeywordsPredicate) other).keywords)); // state check
    }

}
