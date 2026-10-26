package dev.swirlit.indezy.dto;

/** How many of the picked records were deleted, and how many were kept because something still depends on them. */
public record BulkDeleteResult(int deleted, int kept) {
}
