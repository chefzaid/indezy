/** One record the global search found; a note's hit names its opportunity. */
export interface SearchHit {
  id: number;
  title: string;
  subtitle?: string;
  /** The passage the words were found in, when not in the title or subtitle. */
  excerpt?: string;
  projectId?: number;
  /** The member a member's or a post's hit leads to. */
  handle?: string;
}

export interface GlobalSearchResult {
  projects: SearchHit[];
  clients: SearchHit[];
  contacts: SearchHit[];
  notes: SearchHit[];
  /** The network's members and posts, for a member while the network is on. */
  members: SearchHit[];
  posts: SearchHit[];
}
