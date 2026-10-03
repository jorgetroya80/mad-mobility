/** @type {import('@commitlint/types').UserConfig} */
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'perf', 'refactor', 'test', 'docs', 'build', 'ci', 'chore', 'revert'],
    ],
    'scope-enum': [2, 'always', ['api', 'web', 'contract', 'ci', 'deps', 'docs', 'repo']],
  },
};
