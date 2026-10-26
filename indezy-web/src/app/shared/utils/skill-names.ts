/**
 * One name per skill, whatever its spelling, as the server reads them: "Spring Boot", "SpringBoot" and "spring-boot"
 * share a key, and the known aliases ("JS" for JavaScript) name the skill they stand for.
 */
export function skillKey(skill: string | null | undefined): string {
  return (skill ?? '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^\p{L}\p{N}+#.]+/gu, ' ').trim().replace(/[ .]/g, '');
}

export class SkillNames {
  static readonly NONE = new SkillNames({});

  private readonly canonicalByKey = new Map<string, string>();

  /** {@code aliases}: each known skill's usual name with the other names it goes by. */
  constructor(private readonly aliases: Record<string, string[]>) {
    for (const [skill, others] of Object.entries(aliases)) {
      for (const name of [skill, ...others]) {
        this.canonicalByKey.set(skillKey(name), skill);
      }
    }
  }

  /** The usual names of the known skills. */
  get known(): string[] {
    return Object.keys(this.aliases);
  }

  /** The skill's usual name when it is a known one or an alias of one, else the skill as written, trimmed. */
  canonical(skill: string): string {
    return this.canonicalByKey.get(skillKey(skill)) ?? skill.trim();
  }

  same(a: string, b: string): boolean {
    return skillKey(this.canonical(a)) === skillKey(this.canonical(b));
  }

  /** Whether {@code typed} begins the skill's usual name or one of its aliases, spellings aside. */
  startsSkill(skill: string, typed: string): boolean {
    const wanted = skillKey(typed);
    return [skill, ...(this.aliases[skill] ?? [])].some(name => skillKey(name).startsWith(wanted));
  }
}
