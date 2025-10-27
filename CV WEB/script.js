// Efek animasi kecil saat scroll
window.addEventListener('scroll', () => {
    document.querySelectorAll('section').forEach(sec => {
        const pos = sec.getBoundingClientRect().top;
        const winHeight = window.innerHeight;

        if (pos < winHeight - 100) {
            sec.style.opacity = '1';
            sec.style.transform = 'translateY(0)';
        } else {
            sec.style.opacity = '0';
            sec.style.transform = 'translateY(50px)';
        }
    });
});
