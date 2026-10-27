package dev.swirlit.indezy.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A workspace's booking link: the weekly hours it offers, the length of a call, the gap kept around each, how early and
 * how far ahead a call can be booked. Its slug is the link's secret part; a new one cuts the old link off.
 */
@Entity
@Table(name = "booking_pages")
@Getter
@Setter
public class BookingPage extends BaseEntity {

    @Column(name = "freelance_id", nullable = false, unique = true)
    private Long freelanceId;

    @Column(name = "slug", nullable = false, unique = true, length = 24)
    private String slug;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes = 15;

    @Column(name = "min_notice_hours", nullable = false)
    private int minNoticeHours = 24;

    @Column(name = "max_days_ahead", nullable = false)
    private int maxDaysAhead = 21;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_windows", joinColumns = @JoinColumn(name = "page_id"))
    private List<BookingWindow> windows = new ArrayList<>();

    public List<BookingWindow> getWindows() {
        return List.copyOf(windows);
    }

    public void setWindows(List<BookingWindow> windows) {
        this.windows = new ArrayList<>(windows);
    }
}
