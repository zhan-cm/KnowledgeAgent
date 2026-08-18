const $ = (sel) => document.querySelector(sel);

let token = localStorage.getItem('ka_token') || '';
let currentUser = JSON.parse(localStorage.getItem('ka_user') || 'null');
let kbs = [];
let currentKb = null;
let conversations = [];
let currentConversation = null;
let docsTimer = null;

/* ---------------- 基础请求 ---------------- */

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const resp = await fetch(path, { ...options, headers });
  if (resp.status === 401 && !path.startsWith('/api/auth')) {
    logout();
    throw new Error('登录已过期，请重新登录');
  }
  const body = await resp.json().catch(() => ({}));
  if (body.code !== 0) throw new Error(body.message || '请求失败');
  return body.data;
}

function showAuth() {
  $('#auth-view').classList.remove('hidden');
  $('#main-view').classList.add('hidden');
}

function showMain() {
  $('#auth-view').classList.add('hidden');
  $('#main-view').classList.remove('hidden');
  $('#current-user').textContent = currentUser ? currentUser.username : '';
}

function logout() {
  token = '';
  currentUser = null;
  localStorage.removeItem('ka_token');
  localStorage.removeItem('ka_user');
  showAuth();
}

/* ---------------- 登录 / 注册 ---------------- */

let authMode = 'login';

function initAuth() {
  $('#tab-login').addEventListener('click', () => switchAuthMode('login'));
  $('#tab-register').addEventListener('click', () => switchAuthMode('register'));
  $('#auth-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('#auth-error').classList.add('hidden');
    const payload = {
      username: $('#auth-username').value.trim(),
      password: $('#auth-password').value,
    };
    if (authMode === 'register') payload.email = $('#auth-email').value.trim();
    $('#auth-submit').disabled = true;
    try {
      if (authMode === 'login') {
        const data = await api('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) });
        token = data.token;
        currentUser = data.user;
      } else {
        await api('/api/auth/register', { method: 'POST', body: JSON.stringify(payload) });
        const data = await api('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) });
        token = data.token;
        currentUser = data.user;
      }
      localStorage.setItem('ka_token', token);
      localStorage.setItem('ka_user', JSON.stringify(currentUser));
      $('#auth-form').reset();
      enterApp();
    } catch (err) {
      $('#auth-error').textContent = err.message;
      $('#auth-error').classList.remove('hidden');
    } finally {
      $('#auth-submit').disabled = false;
    }
  });
}

function switchAuthMode(mode) {
  authMode = mode;
  $('#tab-login').classList.toggle('active', mode === 'login');
  $('#tab-register').classList.toggle('active', mode === 'register');
  $('#auth-email').classList.toggle('hidden', mode === 'login');
  $('#auth-submit').textContent = mode === 'login' ? '登录' : '注册';
  $('#auth-error').classList.add('hidden');
}

/* ---------------- 知识库 ---------------- */

async function loadKbs() {
  kbs = await api('/api/kbs');
  const list = $('#kb-list');
  list.innerHTML = '';
  if (!kbs.length) {
    list.innerHTML = '<li class="empty-tip">暂无知识库，点 ＋ 创建</li>';
    return;
  }
  kbs.forEach((kb) => {
    const li = document.createElement('li');
    li.textContent = kb.name;
    li.dataset.id = kb.id;
    if (currentKb && currentKb.id === kb.id) li.classList.add('active');
    li.addEventListener('click', () => selectKb(kb));
    list.appendChild(li);
  });
}

async function selectKb(kb) {
  currentKb = kb;
  currentConversation = null;
  $('#chat-title').textContent = kb.name;
  $('#chat-messages').innerHTML = '<div class="empty-tip">选择一个已有对话开始提问，或点左侧 ＋ 新建对话</div>';
  loadKbs();
  await loadConversations();
  loadDocs();
}

async function createKb() {
  const name = prompt('知识库名称：');
  if (!name) return;
  try {
    await api('/api/kbs', { method: 'POST', body: JSON.stringify({ name }) });
    await loadKbs();
  } catch (e) {
    alert(e.message);
  }
}

/* ---------------- 对话 ---------------- */

async function loadConversations() {
  conversations = await api('/api/conversations');
  const mine = currentKb
    ? conversations.filter((c) => c.kbId === currentKb.id)
    : conversations;
  const list = $('#conversation-list');
  list.innerHTML = '';
  if (!mine.length) {
    list.innerHTML = '<li class="empty-tip">暂无对话</li>';
    return;
  }
  mine.forEach((conv) => {
    const li = document.createElement('li');
    li.textContent = conv.title || `对话 #${conv.id}`;
    li.dataset.id = conv.id;
    if (currentConversation && currentConversation.id === conv.id) li.classList.add('active');
    li.addEventListener('click', () => selectConversation(conv));
    list.appendChild(li);
  });
}

async function selectConversation(conv) {
  currentConversation = conv;
  $('#chat-title').textContent = `${currentKb ? currentKb.name + ' · ' : ''}${conv.title || '对话'}`;
  loadConversations();
  $('#chat-messages').innerHTML = '';
  try {
    const messages = await api(`/api/conversations/${conv.id}/messages`);
    messages.forEach((m) => renderMessage(m.role, m.content, m.citations));
  } catch (e) {
    /* 忽略 */
  }
  scrollToBottom();
}

async function createConversation() {
  if (!currentKb) {
    alert('请先选择知识库');
    return;
  }
  try {
    const conv = await api('/api/conversations', {
      method: 'POST',
      body: JSON.stringify({ kbId: currentKb.id }),
    });
    await loadConversations();
    await selectConversation(conv);
  } catch (e) {
    alert(e.message);
  }
}

/* ---------------- 消息渲染 ---------------- */

function renderMessage(role, content, citations) {
  const box = document.createElement('div');
  box.className = `msg ${role === 'USER' ? 'user' : 'assistant'}`;
  box.textContent = content || '';
  if (citations && citations.length) {
    const citeBox = document.createElement('div');
    citeBox.className = 'citations';
    citeBox.innerHTML = '<div>📎 引用来源：</div>';
    citations.forEach((c, i) => {
      const div = document.createElement('div');
      div.className = 'cite';
      div.textContent = `[${i + 1}] ${c.title || '文档'}${c.page ? '（第 ' + c.page + ' 页）' : ''}`;
      div.title = c.chunkText || '';
      citeBox.appendChild(div);
    });
    box.appendChild(citeBox);
  }
  $('#chat-messages').appendChild(box);
  scrollToBottom();
}

function scrollToBottom() {
  const box = $('#chat-messages');
  box.scrollTop = box.scrollHeight;
}

/* ---------------- 提问（流式） ---------------- */

async function sendMessage() {
  const input = $('#chat-input');
  const question = input.value.trim();
  if (!question || sending) return;
  if (!currentKb) {
    alert('请先选择知识库');
    return;
  }
  if (!currentConversation) {
    await createConversation();
    if (!currentConversation) return;
  }

  input.value = '';
  renderMessage('USER', question);
  const answerBox = document.createElement('div');
  answerBox.className = 'msg assistant';
  $('#chat-messages').appendChild(answerBox);
  const cursor = document.createElement('span');
  cursor.className = 'cursor';
  answerBox.appendChild(cursor);
  scrollToBottom();

  sending = true;
  $('#btn-send').disabled = true;
  let acc = '';
  try {
    const resp = await fetch(`/api/conversations/${currentConversation.id}/messages/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({ question }),
    });
    if (resp.status === 401) { logout(); return; }
    if (!resp.ok) {
      const body = await resp.json().catch(() => ({}));
      throw new Error(body.message || '请求失败');
    }
    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        const sse = parseSse(raw);
        if (sse.event === 'delta') {
          const delta = JSON.parse(sse.data);
          acc += delta;
          cursor.remove();
          answerBox.textContent = acc;
          answerBox.appendChild(cursor);
          scrollToBottom();
        } else if (sse.event === 'done') {
          const done = JSON.parse(sse.data);
          cursor.remove();
          answerBox.textContent = done.answer || acc;
          renderCitations(answerBox, done.citations);
          scrollToBottom();
        } else if (sse.event === 'error') {
          const err = JSON.parse(sse.data);
          cursor.remove();
          answerBox.textContent = `⚠️ ${err.message || 'AI 服务异常'}`;
        }
      }
    }
  } catch (e) {
    cursor.remove();
    answerBox.textContent = `⚠️ ${e.message}`;
  } finally {
    sending = false;
    $('#btn-send').disabled = false;
    input.focus();
  }
}

function parseSse(raw) {
  let event = 'message';
  const dataLines = [];
  raw.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  });
  return { event, data: dataLines.join('\n') };
}

function renderCitations(box, citations) {
  if (!citations || !citations.length) return;
  const citeBox = document.createElement('div');
  citeBox.className = 'citations';
  citeBox.innerHTML = '<div>📎 引用来源：</div>';
  citations.forEach((c, i) => {
    const div = document.createElement('div');
    div.className = 'cite';
    div.textContent = `[${i + 1}] ${c.title || '文档'}${c.page ? '（第 ' + c.page + ' 页）' : ''}`;
    div.title = c.chunkText || '';
    citeBox.appendChild(div);
  });
  box.appendChild(citeBox);
}

let sending = false;

/* ---------------- 文档管理 ---------------- */

async function loadDocs() {
  if (!currentKb) return;
  try {
    const docs = await api(`/api/documents?kbId=${currentKb.id}`);
    const list = $('#doc-list');
    list.innerHTML = '';
    if (!docs.length) {
      list.innerHTML = '<li class="empty-tip">暂无文档</li>';
      return;
    }
    docs.forEach((doc) => {
      const li = document.createElement('li');
      li.className = 'doc-item';
      const name = document.createElement('span');
      name.textContent = doc.title;
      name.title = `状态：${doc.status}\n上传时间：${doc.createdAt || ''}`;
      const status = document.createElement('span');
      status.className = `status ${doc.status}`;
      status.textContent = doc.status === 'INDEXED' ? '已索引' :
        doc.status === 'FAILED' ? '失败' :
        doc.status === 'INDEXING' ? '索引中' : '排队中';
      const actions = document.createElement('span');
      actions.className = 'actions';
      const reindexBtn = document.createElement('button');
      reindexBtn.textContent = '↻';
      reindexBtn.title = '重新索引';
      reindexBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        await api(`/api/documents/${doc.id}/reindex`, { method: 'POST' });
        loadDocs();
      });
      const delBtn = document.createElement('button');
      delBtn.textContent = '✕';
      delBtn.title = '删除文档';
      delBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        if (!confirm(`确定删除「${doc.title}」？`)) return;
        await api(`/api/documents/${doc.id}`, { method: 'DELETE' });
        loadDocs();
      });
      actions.appendChild(reindexBtn);
      actions.appendChild(delBtn);
      li.appendChild(name);
      li.appendChild(status);
      li.appendChild(actions);
      list.appendChild(li);
    });
  } catch (e) {
    /* 忽略 */
  }
}

async function uploadDoc(file) {
  if (!currentKb) {
    alert('请先选择知识库');
    return;
  }
  const form = new FormData();
  form.append('file', file);
  form.append('kbId', currentKb.id);
  try {
    const resp = await fetch('/api/documents/upload', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
      body: form,
    });
    const body = await resp.json();
    if (body.code !== 0) throw new Error(body.message);
    loadDocs();
  } catch (e) {
    alert(e.message);
  }
}

/* ---------------- 入口 ---------------- */

function enterApp() {
  showMain();
  loadKbs();
}

function initMain() {
  $('#btn-logout').addEventListener('click', logout);
  $('#btn-create-kb').addEventListener('click', createKb);
  $('#btn-new-conversation').addEventListener('click', createConversation);
  $('#chat-form').addEventListener('submit', (e) => {
    e.preventDefault();
    sendMessage();
  });
  $('#btn-toggle-docs').addEventListener('click', () => {
    $('#docs-panel').classList.toggle('hidden');
  });
  $('#btn-close-docs').addEventListener('click', () => {
    $('#docs-panel').classList.add('hidden');
  });
  $('#btn-upload').addEventListener('click', () => $('#file-input').click());
  $('#file-input').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) uploadDoc(file);
    e.target.value = '';
  });
  setInterval(() => {
    if (!$('#docs-panel').classList.contains('hidden') && currentKb) loadDocs();
  }, 5000);
}

initAuth();
initMain();
if (token && currentUser) {
  enterApp();
} else {
  showAuth();
}
