// Progressive enhancement only. Every section is fully visible without JS;
// this adds a quiet entrance and ticks the hero clock.
(function () {
  var root = document.documentElement;
  root.classList.add("js");

  var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var reveals = document.querySelectorAll(".reveal");

  if (reduce || !("IntersectionObserver" in window)) {
    reveals.forEach(function (el) { el.classList.add("is-in"); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("is-in"); io.unobserve(e.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
    reveals.forEach(function (el) { io.observe(el); });
  }

  // Hero presence clock: counts up from where it sits, the way a real pact would.
  var out = document.getElementById("hero-timer");
  if (out) {
    var total = 1 * 3600 + 24 * 60 + 9;
    var pad = function (n) { return n < 10 ? "0" + n : "" + n; };
    var paint = function () {
      var h = Math.floor(total / 3600);
      var m = Math.floor((total % 3600) / 60);
      var s = total % 60;
      out.textContent = h + ":" + pad(m) + ":" + pad(s);
    };
    paint();
    setInterval(function () { total += 1; paint(); }, 1000);
  }
})();
