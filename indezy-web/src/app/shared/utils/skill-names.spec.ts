import { SkillNames, skillKey } from './skill-names';

describe('SkillNames', () => {
  const names = new SkillNames({ JavaScript: ['JS', 'ECMAScript'], 'Spring Boot': ['SpringBoot'], '.NET': ['dotnet'] });

  it('folds the spellings of one skill to one key, as the server does', () => {
    expect(['Spring Boot', 'SpringBoot', 'spring-boot', ' SPRING  boot '].map(skillKey)).toEqual(Array(4).fill('springboot'));
    expect(skillKey('Node.js')).toBe(skillKey('NodeJS'));
    expect(skillKey('C#')).not.toBe(skillKey('C++'));
    expect(skillKey('Éclair')).toBe('eclair');
    expect(skillKey(null)).toBe('');
  });

  it('names a known skill by its usual name and keeps any other as written', () => {
    expect(names.canonical('js')).toBe('JavaScript');
    expect(names.canonical('dot-net')).toBe('.NET');
    expect(names.canonical(' Elixir ')).toBe('Elixir');
    expect(names.same('JS', 'javascript')).toBe(true);
    expect(names.same('Data Bricks', 'databricks')).toBe(true);
    expect(names.same('Java', 'JavaScript')).toBe(false);
    expect(names.known).toEqual(['JavaScript', 'Spring Boot', '.NET']);
    expect(SkillNames.NONE.canonical('JS')).toBe('JS');
  });

  it('tells whether what is typed begins a skill or one of its aliases', () => {
    expect(names.startsSkill('JavaScript', 'ecma')).toBe(true);
    expect(names.startsSkill('JavaScript', 'js')).toBe(true);
    expect(names.startsSkill('Spring Boot', 'spring-b')).toBe(true);
    expect(names.startsSkill('JavaScript', 'type')).toBe(false);
    expect(names.startsSkill('Elixir', 'eli')).toBe(true);
  });
});
