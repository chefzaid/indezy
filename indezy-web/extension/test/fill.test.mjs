import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { valuesOf } from '../fill.js';

test('takes the profile values a form asks for, empty ones left out', () => {
  assert.deepEqual(valuesOf({ firstName: 'Léa', lastName: 'Martin', email: 'lea@example.test', phone: ' ', city: 'Lyon', targetDailyRate: 650,
    linkedin: 'https://www.linkedin.com/in/lea', position: 'Lead Java', availabilityDate: '2027-01-04', github: null }), {
    firstName: 'Léa', lastName: 'Martin', fullName: 'Léa Martin', email: 'lea@example.test', city: 'Lyon', linkedin: 'https://www.linkedin.com/in/lea',
    title: 'Lead Java', rate: '650', availability: '2027-01-04'
  });
  assert.deepEqual(valuesOf(null), {});
});
