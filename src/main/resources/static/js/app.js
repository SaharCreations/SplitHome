const $ = (s) => document.querySelector(s);
let currentHouseholdId = null;
let dashboard = null;
let households = [];
let editingSettlementId = null;
const EXPENSE_PAGE_SIZE = 20;
let expenseItems = [];
let expensePage = 0;
let expenseHasNext = false;
let expenseTotal = 0;
let expenseSearchTimer = null;

const api = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  if (!response.ok) {
    let message = 'Something went wrong.';
    try { message = (await response.json()).error || message; } catch (_) {}
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
};

const money = (value) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const initials = (name) => name.trim().split(/\s+/).slice(0,2).map(x => x[0]?.toUpperCase() || '').join('');
const dateText = (value) => new Intl.DateTimeFormat('en-US', { month:'short', day:'numeric' }).format(new Date(value));

function toast(message) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove('show'), 2400);
}

function showFormError(id, message) {
  const el = $(id);
  el.textContent = message;
  el.classList.remove('hidden');
}

function clearFormError(id) {
  const el = $(id);
  el.textContent = '';
  el.classList.add('hidden');
}

function confirmAction({ title, message, confirmLabel = 'Confirm', danger = false }) {
  return new Promise(resolve => {
    const dialog = $('#confirmDialog');
    const titleEl = $('#confirmTitle');
    const messageEl = $('#confirmMessage');
    const confirmBtn = $('#confirmActionBtn');
    const cancelBtn = $('#confirmCancelBtn');
    const closeBtn = $('#confirmCloseBtn');

    titleEl.textContent = title;
    messageEl.textContent = message;
    confirmBtn.textContent = confirmLabel;
    confirmBtn.classList.toggle('btn-danger-solid', danger);
    confirmBtn.classList.toggle('btn-primary', !danger);

    let settled = false;
    const finish = value => {
      if (settled) return;
      settled = true;
      cleanup();
      if (dialog.open) dialog.close();
      resolve(value);
    };
    const onCancel = () => finish(false);
    const onConfirm = () => finish(true);
    const onBackdrop = event => { if (event.target === dialog) finish(false); };
    const onNativeCancel = event => { event.preventDefault(); finish(false); };
    const cleanup = () => {
      cancelBtn.removeEventListener('click', onCancel);
      closeBtn.removeEventListener('click', onCancel);
      confirmBtn.removeEventListener('click', onConfirm);
      dialog.removeEventListener('click', onBackdrop);
      dialog.removeEventListener('cancel', onNativeCancel);
    };

    cancelBtn.addEventListener('click', onCancel);
    closeBtn.addEventListener('click', onCancel);
    confirmBtn.addEventListener('click', onConfirm);
    dialog.addEventListener('click', onBackdrop);
    dialog.addEventListener('cancel', onNativeCancel);
    dialog.showModal();
  });
}

function showNotice(title, message) {
  const dialog = $('#noticeDialog');
  $('#noticeTitle').textContent = title;
  $('#noticeMessage').textContent = message;
  dialog.showModal();
}

function validPositiveMoneyInput(input) {
  const raw = input.value.trim();
  const amount = Number(raw);
  return raw !== '' && Number.isFinite(amount) && amount > 0;
}

async function loadHouseholds(preferredId = null) {
  const homes = await api('/api/households');
  households = homes;
  const select = $('#householdSelect');
  select.innerHTML = '';
  if (!homes.length) {
    select.innerHTML = '<option>No homes yet</option>';
    select.disabled = true;
    currentHouseholdId = null;
    $('#emptyState').classList.remove('hidden');
    $('#appView').classList.add('hidden');
    renderManageHomes();
    return;
  }
  select.disabled = false;
  homes.forEach(h => select.add(new Option(h.name, h.id)));
  currentHouseholdId = preferredId || currentHouseholdId || homes[0].id;
  if (!homes.some(h => String(h.id) === String(currentHouseholdId))) currentHouseholdId = homes[0].id;
  select.value = currentHouseholdId;
  renderManageHomes();
  await loadDashboard();
}

function renderManageHomes() {
  const list = $('#manageHomeList');
  if (!list) return;
  if (!households.length) {
    list.innerHTML = '<div class="manage-home-empty"><strong>No homes yet.</strong><span>Create your first home to get started.</span></div>';
    return;
  }
  list.innerHTML = households.map(home => {
    const active = String(home.id) === String(currentHouseholdId);
    return `<article class="manage-home-row ${active ? 'active' : ''}">
      <button class="manage-home-open" type="button" data-open-home="${home.id}">
        <span class="manage-home-icon">⌂</span>
        <span><strong>${escapeHtml(home.name)}</strong><small>${active ? 'Current home' : 'Open this home'}</small></span>
      </button>
      <button class="manage-home-delete" type="button" data-delete-home="${home.id}" aria-label="Delete ${escapeHtml(home.name)}">Delete</button>
    </article>`;
  }).join('');

  list.querySelectorAll('[data-open-home]').forEach(btn => btn.addEventListener('click', async () => {
    currentHouseholdId = Number(btn.dataset.openHome);
    $('#householdSelect').value = currentHouseholdId;
    $('#manageHomeDialog').close();
    await loadDashboard();
    renderManageHomes();
  }));
  list.querySelectorAll('[data-delete-home]').forEach(btn => btn.addEventListener('click', async (event) => {
    event.stopPropagation();
    const id = Number(btn.dataset.deleteHome);
    const home = households.find(h => Number(h.id) === id);
    if (home) await deleteHomeById(id, home.name);
  }));
}

async function loadDashboard() {
  if (!currentHouseholdId) return;
  dashboard = await api(`/api/households/${currentHouseholdId}/dashboard`);
  $('#emptyState').classList.add('hidden');
  $('#appView').classList.remove('hidden');
  $('#homeName').textContent = dashboard.household.name;
  $('#totalSpent').textContent = money(dashboard.totalSpent);
  $('#memberCount').textContent = dashboard.members.length;
  $('#openBalanceCount').textContent = dashboard.debts.length;
  renderPeople();
  renderDebts();
  renderPayments();
  refreshMemberControls();
  await loadExpenses(true);
}

function renderPeople() {
  const balanceMap = new Map(dashboard.balances.map(b => [b.memberId, Number(b.balance)]));
  $('#peopleList').innerHTML = dashboard.members.length ? dashboard.members.map(m => {
    const balance = balanceMap.get(m.id) || 0;
    const cls = balance > 0.009 ? 'positive' : balance < -0.009 ? 'negative' : 'neutral';
    const label = balance > 0.009 ? `gets ${money(balance)}` : balance < -0.009 ? `owes ${money(Math.abs(balance))}` : 'settled';
    return `<div class="person-row">
      <div class="person-chip"><span class="avatar">${initials(m.name)}</span><span>${escapeHtml(m.name)}</span></div>
      <div class="person-row-actions">
        <span class="person-balance ${cls}">${label}</span>
        <button class="delete-btn person-remove-btn" type="button" data-remove-member="${m.id}" aria-label="Remove ${escapeHtml(m.name)}">Remove</button>
      </div>
    </div>`;
  }).join('') : '<div class="all-set"><strong>No people yet</strong>Add the people who share expenses in this home.</div>';

  document.querySelectorAll('[data-remove-member]').forEach(btn => btn.addEventListener('click', () => removeMember(btn.dataset.removeMember)));
}

function renderDebts() {
  if (!dashboard.debts.length) {
    $('#debtList').innerHTML = '<div class="all-set"><strong>All settled.</strong>No one owes anyone right now.</div>';
    return;
  }

  const grouped = new Map();
  dashboard.debts.forEach(debt => {
    const key = String(debt.fromMemberId ?? debt.fromMemberName);
    if (!grouped.has(key)) {
      grouped.set(key, {
        memberId: debt.fromMemberId,
        memberName: debt.fromMemberName,
        debts: []
      });
    }
    grouped.get(key).debts.push(debt);
  });

  $('#debtList').innerHTML = [...grouped.values()].map(group => `
    <article class="debtor-row">
      <div class="debtor-person">
        <span class="avatar">${initials(group.memberName)}</span>
        <span class="debtor-name">${escapeHtml(group.memberName)}</span>
      </div>
      <div class="creditor-list" aria-label="People ${escapeHtml(group.memberName)} owes">
        ${group.debts.map(debt => `
          <div class="creditor-item">
            <span class="creditor-label">owes <strong>${escapeHtml(debt.toMemberName)}</strong></span>
            <strong class="debt-amount">${money(debt.amount)}</strong>
          </div>`).join('')}
      </div>
    </article>`).join('');
}

async function loadExpenses(reset = true) {
  if (!currentHouseholdId) return;
  if (reset) { expensePage = 0; expenseItems = []; }
  const search = encodeURIComponent($('#expenseSearch').value.trim());
  const result = await api(`/api/households/${currentHouseholdId}/expenses?page=${expensePage}&size=${EXPENSE_PAGE_SIZE}&search=${search}`);
  expenseItems = reset ? result.items : [...expenseItems, ...result.items];
  expenseHasNext = result.hasNext;
  expenseTotal = result.totalItems;
  renderExpenses();
}

function renderExpenses() {
  $('#expenseList').innerHTML = expenseItems.length ? expenseItems.map(e => {
    const sharedWith = e.shares.map(s => s.memberName).join(', ');
    const debtorShares = e.shares.filter(s => Number(s.memberId) !== Number(e.paidById));
    const settlementControls = debtorShares.length
      ? debtorShares.map(s => s.settled
          ? `<span class="settled-chip">✓ ${escapeHtml(s.memberName)} settled</span>`
          : `<button class="item-settle-btn" type="button" data-settle-expense="${e.id}" data-settle-member="${s.memberId}">Settle ${escapeHtml(s.memberName)} · ${money(s.remainingAmount)}</button>`
        ).join('')
      : '<span class="settled-chip">No repayment needed</span>';
    const allSettled = debtorShares.length > 0 && debtorShares.every(s => s.settled);
    return `<article class="expense-row expense-row-rich">
      <div class="expense-main"><div class="expense-title-line"><div class="expense-title">${escapeHtml(e.description)}</div>${allSettled ? '<span class="expense-status">Settled</span>' : ''}</div>
      <div class="expense-meta">Paid by ${escapeHtml(e.paidByName)} · split with ${escapeHtml(sharedWith)} · ${dateText(e.createdAt)}</div>
      <div class="expense-settlement-actions">${settlementControls}</div></div>
      <div class="expense-amount">${money(e.amount)}</div>
      <button class="delete-btn" type="button" data-delete-expense="${e.id}">Delete</button>
    </article>`;
  }).join('') : '<div class="all-set"><strong>No expenses found.</strong>Add a shared expense or change your search.</div>';

  const footer = $('#expenseListFooter');
  footer.classList.toggle('hidden', expenseTotal <= EXPENSE_PAGE_SIZE);
  $('#expenseListCount').textContent = `Showing ${expenseItems.length} of ${expenseTotal}`;
  $('#showMoreExpensesBtn').classList.toggle('hidden', !expenseHasNext);
  document.querySelectorAll('[data-delete-expense]').forEach(btn => btn.addEventListener('click', () => deleteExpense(btn.dataset.deleteExpense)));
  document.querySelectorAll('[data-settle-expense]').forEach(btn => btn.addEventListener('click', () => settleExpenseShare(btn.dataset.settleExpense, btn.dataset.settleMember)));
}

function renderPayments() {
  const list = $('#paymentList');
  if (!dashboard.settlements.length) {
    list.innerHTML = '<div class="all-set"><strong>No payments yet.</strong>Payments you record will appear here so they can be corrected or deleted.</div>';
    return;
  }

  list.innerHTML = dashboard.settlements.map(p => `
    <article class="payment-row">
      <div>
        <div class="payment-title">${escapeHtml(p.fromMemberName)} → ${escapeHtml(p.toMemberName)}</div>
        <div class="expense-meta">${p.expenseId ? `For ${escapeHtml(p.expenseDescription)} · ` : 'General payment · '}${dateText(p.createdAt)}</div>
      </div>
      <strong>${money(p.amount)}</strong>
      <div class="payment-actions">
        <button class="text-button" type="button" data-edit-payment="${p.id}">Edit</button>
        <button class="delete-btn" type="button" data-delete-payment="${p.id}">Delete</button>
      </div>
    </article>`).join('');

  document.querySelectorAll('[data-edit-payment]').forEach(btn => btn.addEventListener('click', () => openEditPayment(btn.dataset.editPayment)));
  document.querySelectorAll('[data-delete-payment]').forEach(btn => btn.addEventListener('click', () => deletePayment(btn.dataset.deletePayment)));
}

function refreshMemberControls() {
  const options = dashboard.members.map(m => `<option value="${m.id}">${escapeHtml(m.name)}</option>`).join('');
  $('#paidBySelect').innerHTML = options;
  $('#fromMemberSelect').innerHTML = options;
  $('#toMemberSelect').innerHTML = options;
  $('#participantChecks').innerHTML = dashboard.members.map(m => `<label class="check-item"><input type="checkbox" value="${m.id}" checked><span>${escapeHtml(m.name)}</span></label>`).join('');
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[ch]));
}

async function createHome() {
  clearFormError('#homeError');
  const form = $('#homeForm');
  if (!form.reportValidity()) return;
  const name = $('#homeNameInput').value.trim();
  if (!name) return showFormError('#homeError', 'Home name is required.');
  try {
    const home = await api('/api/households', { method:'POST', body: JSON.stringify({ name }) });
    $('#homeDialog').close(); form.reset();
    await loadHouseholds(home.id); toast('Home created.');
  } catch (e) { showFormError('#homeError', e.message); }
}

async function addPerson() {
  clearFormError('#personError');
  const form = $('#personForm');
  if (!form.reportValidity()) return;
  const name = $('#personNameInput').value.trim();
  if (!name) return showFormError('#personError', 'Name is required.');
  if (!currentHouseholdId) return showFormError('#personError', 'Choose a home first.');
  try {
    await api(`/api/households/${currentHouseholdId}/members`, { method:'POST', body: JSON.stringify({ name }) });
    $('#personDialog').close(); form.reset(); await loadDashboard(); toast('Person added.');
  } catch (e) { showFormError('#personError', e.message); }
}


async function removeMember(id) {
  const member = dashboard.members.find(m => String(m.id) === String(id));
  if (!member) return;
  const balance = dashboard.balances.find(b => String(b.memberId) === String(id));
  const amount = Number(balance?.balance || 0);
  if (Math.abs(amount) > 0.009) {
    toast(`${member.name} must be settled before they can be removed.`);
    return;
  }
  if (!await confirmAction({ title: `Remove ${member.name}?`, message: 'Their name will stay on old expense and payment history.', confirmLabel: 'Remove person', danger: true })) return;
  try {
    await api(`/api/households/${currentHouseholdId}/members/${id}`, { method:'DELETE' });
    await loadDashboard();
    toast(`${member.name} removed.`);
  } catch (e) { showNotice('Couldn’t complete that', e.message); }
}

async function addExpense() {
  clearFormError('#expenseError');
  if (dashboard.members.length < 1) return showFormError('#expenseError', 'Add at least one person first.');
  const form = $('#expenseForm');
  const description = $('#expenseDescription').value.trim();
  const amountInput = $('#expenseAmount');
  const paidById = Number($('#paidBySelect').value);
  const participantIds = [...document.querySelectorAll('#participantChecks input:checked')].map(x => Number(x.value));

  if (!description) return showFormError('#expenseError', 'Expense name is required.');
  if (!validPositiveMoneyInput(amountInput)) return showFormError('#expenseError', 'Price is required and must be a number greater than 0.');
  if (!paidById) return showFormError('#expenseError', 'Choose who paid.');
  if (!participantIds.length) return showFormError('#expenseError', 'Choose at least one person to share this expense.');
  if (!form.reportValidity()) return;

  const amount = Number(amountInput.value);
  try {
    await api(`/api/households/${currentHouseholdId}/expenses`, { method:'POST', body: JSON.stringify({ description, amount, paidById, participantIds }) });
    $('#expenseDialog').close(); form.reset(); await loadDashboard(); toast('Expense added.');
  } catch (e) { showFormError('#expenseError', e.message); }
}

async function recordSettlement() {
  clearFormError('#settleError');
  const fromMemberId = Number($('#fromMemberSelect').value);
  const toMemberId = Number($('#toMemberSelect').value);
  const amountInput = $('#settlementAmount');
  if (!validPositiveMoneyInput(amountInput)) return showFormError('#settleError', 'Amount is required and must be a number greater than 0.');
  if (fromMemberId === toMemberId) return showFormError('#settleError', 'The payer and recipient must be different people.');
  const amount = Number(amountInput.value);
  try {
    await api(`/api/households/${currentHouseholdId}/settlements`, { method:'POST', body: JSON.stringify({ fromMemberId, toMemberId, amount }) });
    $('#settleDialog').close(); $('#settleForm').reset(); await loadDashboard(); toast('Payment recorded.');
  } catch (e) { showFormError('#settleError', e.message); }
}

async function settleExpenseShare(expenseId, memberId) {
  const expense = expenseItems.find(e => String(e.id) === String(expenseId));
  const share = expense?.shares.find(s => String(s.memberId) === String(memberId));
  if (!expense || !share) return;
  if (!await confirmAction({ title: `Settle ${share.memberName}?`, message: `Record ${money(share.remainingAmount)} as settled for “${expense.description}”?`, confirmLabel: 'Settle expense' })) return;
  try {
    await api(`/api/households/${currentHouseholdId}/expenses/${expenseId}/shares/${memberId}/settle`, { method:'POST' });
    await loadDashboard();
    toast(`${expense.description} settled for ${share.memberName}.`);
  } catch (e) { showNotice('Couldn’t complete that', e.message); }
}

function openEditPayment(id) {
  const payment = dashboard.settlements.find(p => String(p.id) === String(id));
  if (!payment) return;
  editingSettlementId = Number(id);
  clearFormError('#editPaymentError');
  $('#editPaymentSummary').textContent = `${payment.fromMemberName} paid ${payment.toMemberName}${payment.expenseId ? ` for ${payment.expenseDescription}` : ''}.`;
  $('#editPaymentAmount').value = Number(payment.amount).toFixed(2);
  $('#editPaymentDialog').showModal();
}

async function savePaymentEdit() {
  clearFormError('#editPaymentError');
  const input = $('#editPaymentAmount');
  if (!validPositiveMoneyInput(input)) return showFormError('#editPaymentError', 'Amount is required and must be a number greater than 0.');
  try {
    await api(`/api/households/${currentHouseholdId}/settlements/${editingSettlementId}`, {
      method:'PUT',
      body: JSON.stringify({ amount: Number(input.value) })
    });
    $('#editPaymentDialog').close();
    editingSettlementId = null;
    await loadDashboard();
    toast('Payment corrected.');
  } catch (e) { showFormError('#editPaymentError', e.message); }
}

async function deletePayment(id) {
  const payment = dashboard.settlements.find(p => String(p.id) === String(id));
  if (!payment) return;
  const detail = payment.expenseId ? ` for "${payment.expenseDescription}"` : '';
  if (!await confirmAction({ title: 'Delete payment?', message: `${money(payment.amount)} from ${payment.fromMemberName} to ${payment.toMemberName}${detail} will be removed and balances recalculated.`, confirmLabel: 'Delete payment', danger: true })) return;
  try {
    await api(`/api/households/${currentHouseholdId}/settlements/${id}`, { method:'DELETE' });
    await loadDashboard();
    toast('Payment deleted.');
  } catch (e) { showNotice('Couldn’t complete that', e.message); }
}

async function deleteExpense(id) {
  if (!await confirmAction({ title: 'Delete this expense?', message: 'Any payments tied specifically to it will also be deleted and balances will be recalculated.', confirmLabel: 'Delete expense', danger: true })) return;
  try {
    await api(`/api/households/${currentHouseholdId}/expenses/${id}`, { method:'DELETE' });
    await loadDashboard(); toast('Expense deleted.');
  } catch (e) { showNotice('Couldn’t complete that', e.message); }
}

async function deleteHomeById(id, name) {
  if (!id) return;
  if (!await confirmAction({ title: `Delete “${name}”?`, message: 'This permanently deletes this home, its people, expenses, settlements, and payment history. This cannot be undone.', confirmLabel: 'Delete home', danger: true })) return;
  try {
    await api(`/api/households/${id}`, { method:'DELETE' });
    if (String(currentHouseholdId) === String(id)) currentHouseholdId = null;
    await loadHouseholds();
    renderManageHomes();
    toast('Home deleted.');
  } catch (e) { showNotice('Couldn’t complete that', e.message); }
}

async function deleteCurrentHome() {
  if (!currentHouseholdId || !dashboard) return;
  return deleteHomeById(currentHouseholdId, dashboard.household.name);
}


$('#manageHomeBtn').addEventListener('click', () => { renderManageHomes(); $('#manageHomeDialog').showModal(); });
$('#newHomeFromManagerBtn').addEventListener('click', () => { $('#manageHomeDialog').close(); clearFormError('#homeError'); $('#homeDialog').showModal(); });
$('#emptyCreateBtn').addEventListener('click', () => { clearFormError('#homeError'); $('#homeDialog').showModal(); });
$('#addPersonBtn').addEventListener('click', () => { clearFormError('#personError'); $('#personDialog').showModal(); });
$('#addExpenseBtn').addEventListener('click', () => { clearFormError('#expenseError'); dashboard.members.length ? $('#expenseDialog').showModal() : toast('Add a person first.'); });
$('#settleBtn').addEventListener('click', () => { clearFormError('#settleError'); dashboard.members.length >= 2 ? $('#settleDialog').showModal() : toast('Add at least two people first.'); });
$('#saveHomeBtn').addEventListener('click', createHome);
$('#savePersonBtn').addEventListener('click', addPerson);
$('#saveExpenseBtn').addEventListener('click', addExpense);
$('#saveSettlementBtn').addEventListener('click', recordSettlement);
$('#savePaymentEditBtn').addEventListener('click', savePaymentEdit);
$('#expenseSearch').addEventListener('input', () => { clearTimeout(expenseSearchTimer); expenseSearchTimer = setTimeout(() => loadExpenses(true).catch(e => toast(e.message)), 250); });
$('#showMoreExpensesBtn').addEventListener('click', async () => { if (!expenseHasNext) return; expensePage += 1; try { await loadExpenses(false); } catch (e) { expensePage -= 1; toast(e.message); } });
$('#householdSelect').addEventListener('change', async (e) => { currentHouseholdId = Number(e.target.value); await loadDashboard(); });

$('#noticeCloseBtn').addEventListener('click', () => $('#noticeDialog').close());
$('#noticeDoneBtn').addEventListener('click', () => $('#noticeDialog').close());
$('#noticeDialog').addEventListener('click', event => { if (event.target === $('#noticeDialog')) $('#noticeDialog').close(); });

loadHouseholds().catch(e => showNotice('Couldn’t load SplitHome', e.message));

// Dialogs: close controls must never trigger form validation.
document.querySelectorAll('dialog:not([data-managed-dialog])').forEach(dialog => {
  dialog.querySelectorAll('.dialog-cancel').forEach(button => {
    button.addEventListener('click', () => {
      dialog.close();
      const form = dialog.querySelector('form');
      if (form) form.reset();
      dialog.querySelectorAll('.form-error').forEach(error => {
        error.textContent = '';
        error.classList.add('hidden');
      });
    });
  });

  // Clicking the backdrop closes the dialog without submitting/validating.
  dialog.addEventListener('click', event => {
    if (event.target === dialog) {
      dialog.close();
      const form = dialog.querySelector('form');
      if (form) form.reset();
    }
  });
});
