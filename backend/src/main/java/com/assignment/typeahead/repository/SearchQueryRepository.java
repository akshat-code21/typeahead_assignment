package com.assignment.typeahead.repository;
import com.assignment.typeahead.model.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {
    List<SearchQuery> findByQueryStartingWithIgnoreCase(String prefix);
    SearchQuery findByQuery(String query);
    List<SearchQuery> findTop10ByOrderByCountDesc();
}
