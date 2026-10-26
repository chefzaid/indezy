/** How many of the records picked in a list were deleted, and how many were kept because something depends on them. */
export interface BulkDeleteResult {
  deleted: number;
  kept: number;
}
