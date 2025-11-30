// When plain htmx isn't quite enough, you can stick some custom JS here.

/**
 * Update character counter for content textarea in edit mode
 *
 * @param {HTMLTextAreaElement} textarea - The textarea element
 */
function updateCharCount(textarea) {
  const summaryId = textarea.id.replace('content-textarea-', '');
  const trimmed = textarea.value.trim();
  const length = trimmed.length;
  const counter = document.getElementById(`char-counter-${summaryId}`);
  const saveBtn = document.getElementById(`save-btn-${summaryId}`);

  if (!counter || !saveBtn) return;

  const minLength = 50;
  const maxLength = 50000;

  // Update counter text
  counter.textContent = `${length} / ${maxLength} characters`;

  // Validation and styling
  if (length < minLength) {
    counter.className = 'char-counter text-sm text-red-600 mt-1';
    counter.textContent = `Too short (${length} chars, minimum ${minLength})`;
    saveBtn.disabled = true;
    saveBtn.classList.add('opacity-50', 'cursor-not-allowed');
  } else if (length > maxLength) {
    counter.className = 'char-counter text-sm text-red-600 mt-1';
    counter.textContent = `Too long (${length} chars, maximum ${maxLength})`;
    saveBtn.disabled = true;
    saveBtn.classList.add('opacity-50', 'cursor-not-allowed');
  } else {
    counter.className = 'char-counter text-sm text-gray-600 mt-1';
    saveBtn.disabled = false;
    saveBtn.classList.remove('opacity-50', 'cursor-not-allowed');
  }
}

/**
 * Initialize character counter for new summary form
 * Attaches event listener to content textarea on page load
 */
document.addEventListener('DOMContentLoaded', function() {
  const contentTextarea = document.getElementById('content');
  const charCounter = document.getElementById('char-counter');
  const submitBtn = document.getElementById('submit-btn');

  if (contentTextarea && charCounter && submitBtn) {
    const minLength = 50;
    const maxLength = 50000;

    function updateNewSummaryCharCount() {
      const trimmed = contentTextarea.value.trim();
      const length = trimmed.length;

      if (length < minLength) {
        charCounter.className = 'mt-1 text-sm text-red-600';
        charCounter.textContent = `Too short (${length} chars, minimum ${minLength})`;
        submitBtn.disabled = true;
        submitBtn.classList.add('opacity-50', 'cursor-not-allowed');
      } else if (length > maxLength) {
        charCounter.className = 'mt-1 text-sm text-red-600';
        charCounter.textContent = `Too long (${length} chars, maximum ${maxLength})`;
        submitBtn.disabled = true;
        submitBtn.classList.add('opacity-50', 'cursor-not-allowed');
      } else {
        charCounter.className = 'mt-1 text-sm text-gray-600';
        charCounter.textContent = `${length} / ${maxLength} characters`;
        submitBtn.disabled = false;
        submitBtn.classList.remove('opacity-50', 'cursor-not-allowed');
      }
    }

    // Initialize on page load
    updateNewSummaryCharCount();

    // Update on input
    contentTextarea.addEventListener('input', updateNewSummaryCharCount);
  }
});
