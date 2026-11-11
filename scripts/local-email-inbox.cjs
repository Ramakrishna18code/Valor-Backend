const http=require('http'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..','.local-mailbox');
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
http.createServer((req,res)=>{
 if(!['127.0.0.1:8025','localhost:8025'].includes(req.headers.host)||req.method!=='GET'){res.writeHead(403);res.end('Local inbox only');return;}
 res.setHeader('Cache-Control','no-store');
 res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
 res.setHeader('X-Content-Type-Options','nosniff');
 const messages=fs.existsSync(root)?fs.readdirSync(root).filter(f=>/^[a-f0-9-]+\.json$/.test(f)).flatMap(f=>{try{return [JSON.parse(fs.readFileSync(path.join(root,f),'utf8'))];}catch{return [];}}).sort((a,b)=>b.sentAt.localeCompare(a.sentAt)):[];
 res.setHeader('Content-Type','text/html; charset=utf-8');
 res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Valor local email inbox</title><style>body{font:15px system-ui;background:#f4f7fc;color:#294b68;margin:0;padding:24px}main{max-width:760px;margin:auto}header{background:#123b75;color:white;padding:24px;border-radius:18px}article{background:white;padding:20px;border:1px solid #dce5f0;border-radius:16px;margin-top:14px}p{line-height:1.7;overflow-wrap:anywhere}a{display:inline-block;padding:12px;color:#123b75}small{color:#708699}h1{font-size:23px}h2{font-size:17px}</style></head><body><main><header><h1>Valor local email inbox</h1><p>Development only. Messages stay on this PC and are not delivered to real email addresses.</p></header><a href="/">Refresh inbox</a>'+(messages.length?messages.slice(0,100).map(m=>'<article><small>'+escape(m.sentAt)+'</small><h2>'+escape(m.subject)+'</h2><p>To: '+escape(m.to)+'</p><p>'+escape(m.body)+'</p></article>').join(''):'<article>No emails yet. Create an account in the customer app, then refresh this inbox.</article>')+'</main></body></html>');
}).listen(8025,'127.0.0.1',()=>console.log('Local email inbox: http://127.0.0.1:8025'));
