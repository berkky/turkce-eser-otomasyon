(function(){
  const q = document.getElementById('eser-ara');
  const d = document.getElementById('durum-filtre');
  const b = document.getElementById('buyuk-filtre');
  const o = document.getElementById('onizleme-filtre');
  const rows = document.querySelectorAll('[data-eser-row]');
  function filtre(){
    const metin = (q && q.value || '').toLowerCase();
    const durum = d && d.value || '';
    const buyuk = b && b.value || '';
    const oniz = o && o.value || '';
    rows.forEach(r => {
      const ad = (r.getAttribute('data-ad')||'').toLowerCase();
      const md = r.getAttribute('data-durum')||'';
      const be = r.getAttribute('data-buyuk')||'';
      const oz = r.getAttribute('data-onizleme')||'';
      let g = ad.includes(metin);
      if (durum && md !== durum) g = false;
      if (buyuk === 'evet' && be !== 'true') g = false;
      if (buyuk === 'hayir' && be === 'true') g = false;
      if (oniz === 'evet' && oz !== 'true') g = false;
      if (oniz === 'hayir' && oz === 'true') g = false;
      r.style.display = g ? '' : 'none';
    });
  }
  [q,d,b,o].forEach(el => el && el.addEventListener('input', filtre));

  const audio = document.getElementById('alignment-audio');
  const segs = document.querySelectorAll('.align-seg');
  if (audio && segs.length) {
    function vurgula() {
      const t = audio.currentTime;
      segs.forEach(s => {
        const start = parseFloat(s.getAttribute('data-start') || '0');
        const next = s.nextElementSibling;
        const end = next ? parseFloat(next.getAttribute('data-start') || '9999') : 9999;
        const aktif = t >= start && t < end;
        s.classList.toggle('align-active', aktif);
      });
    }
    audio.addEventListener('timeupdate', vurgula);
    segs.forEach(s => {
      s.addEventListener('click', () => {
        const start = parseFloat(s.getAttribute('data-start') || '0');
        audio.currentTime = start;
        audio.play();
      });
    });
  }
})();
