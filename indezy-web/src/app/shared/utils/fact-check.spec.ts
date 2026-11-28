import { newFacts } from './fact-check';

describe('newFacts', () => {
  const source = 'Lead Java. Migration vers Kafka chez une banque, 12 services. Java, PostgreSQL';

  it('tells the numbers and names the source never states', () => {
    expect(newFacts('Migration de 40 services vers Kafka et Kubernetes, 30 % de coûts en moins.', source))
      .toEqual(['40', 'Kubernetes', '30']);
    expect(newFacts('Migration du SI vers GitLab CI, sur AWS S3 et C#.', source)).toEqual(['SI', 'GitLab', 'CI', 'AWS', 'S3', 'C#']);
  });

  it('tells nothing for wording the source states, a sentence opening or a number it gives', () => {
    expect(newFacts('Migration des 12 services vers Kafka. Postgresql et java.', source)).toEqual([]);
    expect(newFacts('Expert reconnu. Rigoureux, autonome.', source)).toEqual([]);
    expect(newFacts(null, source)).toEqual([]);
  });

  it('tells a figure once and at most eight', () => {
    expect(newFacts('40 40 1 2 3 4 5 6 7 8 9', source)).toEqual(['40', '1', '2', '3', '4', '5', '6', '7']);
  });
});
