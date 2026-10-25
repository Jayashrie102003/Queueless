// app.js: shared helpers used by every QueueLess page.
// Every page loads this file BEFORE its own <script> block.

// An error that keeps the HTTP status (401, 403, 409...) with the message.
class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// Calls the backend and returns the parsed JSON reply.
// Throws ApiError with the server's message if the request fails.
// On 401 (not signed in), sends the user to the login page, unless redirectOn401 is false.
async function api(path, options = {}) {
  const method = options.method || "GET";
  const redirectOn401 = options.redirectOn401 !== false;

  const init = { method: method, credentials: "same-origin", headers: {} };
  if (options.body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }

  let response;
  try {
    response = await fetch(path, init);
  } catch (networkError) {
    // fetch() itself failed: the server is not running or not reachable.
    throw new ApiError(0, "Cannot reach the QueueLess server. Is it running?");
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (parseError) {
      data = { error: text };
    }
  }

  if (!response.ok) {
    if (response.status === 401 && redirectOn401) {
      window.location.href = "login.html";
    }
    const message = (data && data.error) || "Request failed (" + response.status + ")";
    throw new ApiError(response.status, message);
  }
  return data;
}

// Shows a message box. Pass an empty text to hide it.
function showMessage(element, text, type) {
  element.textContent = text;
  element.className = "message " + (type || "info");
  element.hidden = !text;
}

// Creates an element. The text goes in with textContent, never as HTML.
function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}

// The coloured label for a token status.
function statusBadge(status) {
  const labels = {
    WAITING: "Waiting",
    CALLED: "Being served",
    COMPLETED: "Completed",
    ABSENT: "Absent",
    CANCELLED: "Cancelled",
  };
  return el("span", "badge badge-" + status.toLowerCase(), labels[status] || status);
}

// Checks the user is signed in. If a role is given and the user has a different role,
// sends them to their own home page. Returns the user's id and role.
async function requireLogin(role) {
  const me = await api("/api/auth/me");
  if (role && me.role !== role) {
    window.location.href = me.role === "STAFF" ? "staff.html" : "index.html";
    throw new ApiError(403, "This page is for a different kind of account.");
  }
  return me;
}

// Ends the session on the server and returns to the login page.
async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST", redirectOn401: false });
  } catch (err) {
    // Even if the call fails, we still leave the page.
  }
  window.location.href = "login.html";
}