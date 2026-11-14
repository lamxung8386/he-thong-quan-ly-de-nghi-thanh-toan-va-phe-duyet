document.addEventListener('DOMContentLoaded', () => {
  const trigger = document.querySelector('[data-nav-toggle]');
  const nav = document.getElementById('primary-navigation');
  if (trigger && nav) trigger.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    trigger.setAttribute('aria-expanded', String(open));
  });
  document.querySelectorAll('[data-file-input]').forEach(input => {
    input.addEventListener('change', () => {
      const preview = document.querySelector('[data-file-name]');
      if (preview) preview.textContent = input.files?.[0] ? `${input.files[0].name} (${Math.round(input.files[0].size / 1024)} KB)` : 'Chưa có tệp được chọn';
    });
  });
  document.querySelectorAll('.profile-drop').forEach(menu => {
    document.addEventListener('click', ev => { if (!menu.contains(ev.target)) menu.open = false; });
  });
});
