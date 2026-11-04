const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadLab() {
  const filename = path.join(__dirname, '..', 'index.html');
  assert.ok(fs.existsSync(filename), 'standalone learning page must exist');
  const html = fs.readFileSync(filename, 'utf8');
  const script = html.match(/<script id="lab-core">([\s\S]*?)<\/script>/);
  assert.ok(script, 'the standalone page must contain its player core');
  const context = { setTimeout, clearTimeout };
  vm.createContext(context);
  vm.runInContext(script[1], context);
  return context.OrmLab;
}

function fixture() {
  const lab = loadLab();
  const tasks = [];
  const changes = [];
  const player = lab.createPlayer({
    onChange: s => changes.push(s),
    setTimer: (fn, delay) => { const task = { fn, delay, cancelled: false }; tasks.push(task); return task; },
    clearTimer: task => { if (task) task.cancelled = true; }
  });
  return { lab, player, tasks, changes };
}

test('a mapper result can be inspected, rewound, and reset without stale values', () => {
  const { player, lab } = fixture();
  assert.equal(player.snapshot().playing, false);
  while (!player.snapshot().atEnd) player.next();
  assert.match(JSON.stringify(player.snapshot().frame), /Alice/);
  player.prev();
  assert.equal(player.snapshot().playing, false);
  player.restart();
  assert.equal(player.snapshot().index, 0);
  assert.equal(player.snapshot().frame, lab.scenarios.mapper.steps[0]);
});

test('switching scenarios cancels playback and rejects even an already queued old callback', () => {
  const { player, tasks } = fixture();
  player.play();
  const stale = tasks.at(-1);
  player.select('missing');
  assert.equal(stale.cancelled, true);
  stale.fn();
  assert.equal(player.snapshot().scenario, 'missing');
  assert.equal(player.snapshot().index, 0);
  assert.equal(player.snapshot().playing, false);
});

test('pause, manual navigation and speed changes cannot create concurrent playback', () => {
  const { player, tasks } = fixture();
  player.play();
  const old = tasks.at(-1);
  player.setSpeed(2);
  assert.equal(old.cancelled, true);
  assert.ok(tasks.at(-1).delay < old.delay);
  const fast = tasks.at(-1);
  player.pause();
  fast.fn();
  assert.equal(player.snapshot().index, 0);
  player.play();
  player.next();
  assert.equal(player.snapshot().playing, false);
  tasks.at(-1).fn();
  assert.equal(player.snapshot().index, 1);
});

test('autoplay finishes once, preserves its final result, and restarts explicitly', () => {
  const { player, tasks } = fixture();
  player.play();
  for (let guard = 0; player.snapshot().playing && guard < 30; guard++) tasks.at(-1).fn();
  assert.equal(player.snapshot().atEnd, true);
  assert.equal(player.snapshot().playing, false);
  const finalIndex = player.snapshot().index;
  player.next();
  assert.equal(player.snapshot().index, finalIndex);
  player.play();
  assert.equal(player.snapshot().index, 0);
  assert.equal(player.snapshot().playing, true);
  player.pause();
});

test('unmapped statements and Object methods stop before a database operation', () => {
  const { lab } = fixture();
  for (const name of ['missing', 'object']) {
    assert.ok(lab.scenarios[name].steps.every(step => !step.nodes.includes('db')));
  }
  assert.match(JSON.stringify(lab.scenarios.missing.steps.at(-1)), /BindingException/);
  assert.match(JSON.stringify(lab.scenarios.object.steps.at(-1)), /MapperProxy/);
});

test('three Spring proxy paths preserve transaction boundaries', () => {
  const { lab, player } = fixture();
  for (const [scenario, expected] of [['external', [100, 100]], ['self', [70, 100]], ['outer', [100, 100]]]) {
    player.select(scenario);
    while (!player.snapshot().atEnd) player.next();
    assert.equal(JSON.stringify(player.snapshot().frame.balances), JSON.stringify(expected));
    player.prev();
    assert.equal(JSON.stringify(player.snapshot().frame.balances), '[70,100]');
    assert.ok(lab.scenarios[scenario].steps.some(step => /DataAccessException/.test(JSON.stringify(step))));
  }
});

test('invalid speed and scenario input leave the inspected state intact', () => {
  const { player } = fixture();
  const before = JSON.stringify(player.snapshot());
  assert.throws(() => player.select('not-a-scenario'));
  assert.throws(() => player.setSpeed(0));
  assert.equal(JSON.stringify(player.snapshot()), before);
});

// Education contract: the page describes published libraries, and the animation
// must not teach the former miniature implementation as their execution path.
test('page explains actual data access technologies without project branding', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
  assert.doesNotMatch(html, /jorm|자체 구현|구현 완료|미구현|MapperFactory|TransactionProxyFactory/i);
  for (const concept of ['ConversionService', 'EmptyResultDataAccessException', 'IncorrectResultSizeDataAccessException',
    'MappedStatement', 'SqlSession', 'Executor', 'ResultMap', 'TypeHandler',
    'TransactionInterceptor', 'DataSourceTransactionManager', 'rollbackFor', 'rollbackOnly',
    '@MappedSuperclass', '@Transient', 'GenerationType.IDENTITY', '스냅샷', 'dirty checking', 'FlushModeType.AUTO']) {
    assert.ok(html.includes(concept), concept + ' must be explained');
  }
});

test('MyBatis mapper scenes use cached execution and JDBC type handlers', () => {
  const { lab } = fixture();
  const text = JSON.stringify(lab.scenarios.mapper.steps);
  for (const name of ['MappedStatement', 'cachedInvoker', 'SqlSession', 'Executor', 'TypeHandler', 'ResultMap']) assert.ok(text.includes(name), name);
  assert.doesNotMatch(text, /JdbcTemplate|BeanPropertyRowMapper|필드 fallback/);
  assert.match(text, /setLong\(1, 1L\)/);
});

test('a default mapper method executes its body then reenters the mapper proxy', () => {
  const { lab, player } = fixture();
  player.select('default');
  assert.ok(!player.snapshot().frame.nodes.includes('db'));
  while (!player.snapshot().atEnd) player.next();
  assert.match(JSON.stringify(player.snapshot().frame), /Alice/);
  assert.ok(lab.scenarios.default.steps.some(s => s.nodes.includes('db')));
  assert.match(JSON.stringify(lab.scenarios.default.steps), /MethodHandle/);
});
