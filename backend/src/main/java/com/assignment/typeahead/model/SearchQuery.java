package com.assignment.typeahead.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="search_queries",indexes = {
        @Index(name = "idx_query_prefix", columnList = "query"),
        @Index(name = "idx_query_count", columnList = "count DESC")
})
public class SearchQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true,length=500)
    private String query;

    @Column(nullable = false)
    private Long count=0L;

    @Column(name="updated_at",nullable = false)
    private final LocalDateTime updatedAt = LocalDateTime.now();

    public SearchQuery() {
    }

    public SearchQuery(Long id, String query) {
        this.id = id;
        this.query = query;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
