/* ==========================================================================
   Fokus lessons — quiz widget
   Usage: <script src="../assets/quiz.js" defer></script>
   Markup:
     <div class="quiz" data-title="Check your understanding">
       <p class="quiz-title">Check your understanding</p>
       <div class="q" data-why="Optional explanation shown after answering.">
         <p class="q-text">The question</p>
         <div class="options">
           <button class="option" data-correct>Right answer</button>
           <button class="option">Distractor</button>
         </div>
         <p class="feedback"><span class="verdict"></span> <span class="why"></span></p>
       </div>
       <p class="score">Score: 0 / 0</p>
       <button class="reset">Reset quiz</button>
     </div>
   Rules: exactly one .option per question carries data-correct.
   ========================================================================== */
(function () {
  "use strict";

  function initQuiz(quiz) {
    var questions = quiz.querySelectorAll(".q");
    var scoreEl = quiz.querySelector(".score");
    var resetBtn = quiz.querySelector(".reset");
    var total = questions.length;
    var correct = 0;

    function renderScore() {
      if (scoreEl) {
        scoreEl.textContent = "Score: " + correct + " / " + total;
      }
    }

    function lockQuestion(q) {
      q.querySelectorAll(".option").forEach(function (opt) {
        opt.disabled = true;
      });
    }

    function handleAnswer(q, clicked) {
      if (q.classList.contains("answered")) return;
      var isCorrect = clicked.hasAttribute("data-correct");
      q.classList.add("answered", isCorrect ? "correct" : "wrong");
      clicked.classList.add(isCorrect ? "correct" : "wrong");
      q.querySelectorAll(".option").forEach(function (opt) {
        if (opt.hasAttribute("data-correct") && opt !== clicked) {
          opt.classList.add("correct");
        }
      });
      lockQuestion(q);

      var verdict = q.querySelector(".feedback .verdict");
      if (verdict) {
        verdict.textContent = isCorrect ? "Correct." : "Not quite.";
      }
      var why = q.querySelector(".feedback .why");
      if (why && q.hasAttribute("data-why")) {
        why.textContent = " " + q.getAttribute("data-why");
      }

      if (isCorrect) correct += 1;
      renderScore();
    }

    questions.forEach(function (q) {
      q.querySelectorAll(".option").forEach(function (opt) {
        opt.addEventListener("click", function () {
          handleAnswer(q, opt);
        });
      });
    });

    if (resetBtn) {
      resetBtn.addEventListener("click", function () {
        correct = 0;
        questions.forEach(function (q) {
          q.classList.remove("answered", "correct", "wrong");
          q.querySelectorAll(".option").forEach(function (opt) {
            opt.classList.remove("correct", "wrong");
            opt.disabled = false;
          });
        });
        renderScore();
      });
    }

    renderScore();
  }

  function init() {
    document.querySelectorAll(".quiz").forEach(initQuiz);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();