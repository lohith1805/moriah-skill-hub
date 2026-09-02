import { mockRequest } from "./apiClient";
import { USERS, BATCHES } from "./mockData";
import { ROLES, ACCOUNT_STATUS } from "../utils/constants";

const REGISTERED_USERS_KEY = "mORIAH_REGISTERED_USERS";

function readRegisteredUsers() {
  try {
    const raw = localStorage.getItem(REGISTERED_USERS_KEY);
    let list = [];
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          list = parsed;
        }
      } catch (e) {
        list = [];
      }
    }

    // Clean up duplicate users (keep only the latest one per email)
    const uniqueMap = new Map();
    list.forEach((u) => {
      if (u && u.email) {
        uniqueMap.set(u.email.toLowerCase(), u);
      } else if (u && u.id) {
        uniqueMap.set(`no-email-${u.id}`, u);
      }
    });
    list = Array.from(uniqueMap.values());

    // Default Admin configuration
    const defaultAdminEmail = "admin@moriah.io";
    const hasAdmin = list.some((u) => u.email && u.email.toLowerCase() === defaultAdminEmail.toLowerCase());

    if (!hasAdmin) {
      const defaultAdmin = {
        id: "admin-default",
        name: "Admin User",
        email: defaultAdminEmail,
        phone: "+919999999999",
        role: ROLES.ADMIN,
        password: "Password123",
        accountStatus: ACCOUNT_STATUS.ACTIVE,
        avatarColor: "#0D2845",
      };
      list.push(defaultAdmin);
    }

    localStorage.setItem(REGISTERED_USERS_KEY, JSON.stringify(list));
    return list;
  } catch (err) {
    console.warn("[authService] Failed to read custom registered users:", err);
    return [];
  }
}

function writeRegisteredUsers(list) {
  try {
    localStorage.setItem(REGISTERED_USERS_KEY, JSON.stringify(list));
  } catch (err) {
    console.warn("[authService] Failed to save custom registered users:", err);
  }
}

// In production this posts to POST /auth/login and receives a JWT + refresh token (SRS §5.1).
export async function login({ identifier, password }) {
  if (!identifier || !password) throw new Error("Identifier and password are required");

  const cleanIdentifier = identifier.trim().toLowerCase();
  const list = readRegisteredUsers();

  const userMatches = (u) => {
    // Match email
    if (u.email && u.email.toLowerCase() === cleanIdentifier) return true;
    // Match phone
    if (u.phone) {
      const cleanPhone = u.phone.replace(/[\s\-\+\(\)]/g, "");
      const cleanInputPhone = cleanIdentifier.replace(/[\s\-\+\(\)]/g, "");
      if (cleanPhone === cleanInputPhone && cleanPhone.length > 0) return true;
    }
    // Match name (username) case-insensitively
    if (u.name && u.name.toLowerCase() === cleanIdentifier) return true;
    // Match email prefix as a fallback for username (e.g. "ananya.student" for "ananya.student@moriah.io")
    if (u.email) {
      const prefix = u.email.split("@")[0].toLowerCase();
      if (prefix === cleanIdentifier) return true;
    }
    return false;
  };

  // 1. Look in registered users first
  let user = list.find(userMatches);

  // 2. If not found, check if they match a pre-seeded user (none by default)
  if (!user) {
    const mockUser = USERS.find(userMatches);
    if (mockUser) {
      // Create a registered user on the fly from the mock user
      user = {
        ...mockUser,
        password: password, // accept whatever password they typed to register/log in
        accountStatus: ACCOUNT_STATUS.ACTIVE,
      };
      list.push(user);
      writeRegisteredUsers(list);
    }
  }

  // 3. Verify password
  if (user) {
    if (user.password && user.password !== password) {
      throw new Error("Invalid password. Please try again.");
    }
    // No password on file yet — either a brand-new mock-user login (handled
    // above) or an account that just went through requestPasswordReset().
    // Lock in whatever was typed just now as the new password, same rule as
    // first-time login, so a reset account doesn't stay wide-open forever.
    if (!user.password) {
      user = { ...user, password };
      const idx = list.findIndex((u) => u.id === user.id);
      if (idx > -1) {
        list[idx] = user;
        writeRegisteredUsers(list);
      }
    }
  } else {
    throw new Error("Invalid login details. Please register first.");
  }

  // 4. Gate login on account lifecycle state (mirrors backend's account_status check)
  if (user.accountStatus === ACCOUNT_STATUS.PENDING_APPROVAL) {
    throw new Error("Your account is still awaiting Admin approval. We'll notify you once it's reviewed.");
  }
  if (user.accountStatus === ACCOUNT_STATUS.REJECTED) {
    throw new Error("This registration request was not approved. Contact support for details.");
  }
  if (user.accountStatus === ACCOUNT_STATUS.INVITED) {
    throw new Error("Please finish setting your password from the invite link before signing in.");
  }

  const token = `mock.${btoa(user.id)}.${Date.now()}`;
  await mockRequest(null, { delay: 600 });
  return { user, token };
}

// Student self-registration — Option A: subscription + payment happen inside
// the registration wizard itself, so a student account is only ever created
// once payment has succeeded. `subscription` is the plan/payment summary
// gathered by the Register wizard's plan + payment steps.
export async function registerStudent(payload) {
  const { subscription, ...rest } = payload;

  const track = rest.track || "Full-Stack Development";
  // Prefer a batch matching the student's track. If none exists (e.g. no
  // batch has been created for that track yet), fall back to the most
  // recently created batch of ANY track — BATCHES is unshift-ordered, so
  // BATCHES[0] is the newest — rather than inventing a batch name that
  // doesn't exist. A student must always land on a real batch object (or
  // none at all) so their enrollment is never silently lost from every
  // batch's roster/count.
  // Automatic batch allocation is disabled. Student must be assigned by a trainer.
  const assignedBatchName = null;

  // NOTE: batch student counts are no longer tracked via a manual counter
  // here — trainerService.getBatches() computes each batch's student count
  // live from the registered-user roster, so it can never drift out of
  // sync with who's actually assigned to that batch (same fix as health).

  const newUser = {
    ...USERS[0],
    ...rest,
    id: `u${Date.now()}`,
    role: ROLES.STUDENT,
    accountStatus: ACCOUNT_STATUS.ACTIVE,
    track,
    batch: assignedBatchName,
    subscription: subscription || null,
  };

  const list = readRegisteredUsers();
  const existingIdx = list.findIndex((u) => u.email && u.email.toLowerCase() === newUser.email.toLowerCase());
  if (existingIdx > -1) {
    list[existingIdx] = newUser;
  } else {
    list.push(newUser);
  }
  writeRegisteredUsers(list);

  // Record the payment so it shows up for Admin's Transactions dashboard —
  // mirrors what a real payment-gateway webhook would write.
  if (subscription) {
    try {
      const rawTx = localStorage.getItem("msh_transactions");
      const txList = rawTx ? JSON.parse(rawTx) : [];
      const txId = `tx${Date.now()}`;
      txList.unshift({
        id: txId,
        student: newUser.name,
        plan: subscription.planName || subscription.planCode || "Subscription",
        amount: subscription.price || subscription.amount || 0,
        gateway: subscription.gateway || "Razorpay",
        status: "Success",
        date: new Date().toISOString().slice(0, 10),
      });
      localStorage.setItem("msh_transactions", JSON.stringify(txList));

      // Record welcome and receipt confirmation notification
      const rawNotif = localStorage.getItem("msh_notifications");
      const notifList = rawNotif ? JSON.parse(rawNotif) : [];
      notifList.unshift({
        id: `notif-${Date.now()}`,
        title: "Welcome & Payment Confirmed",
        body: `Hi ${newUser.name.split(" ")[0]}! Your registration payment of ₹${subscription.price || subscription.amount || 0} for the ${subscription.planName} plan has been processed successfully. Invoice: ${txId.replace("tx", "INV-")}.`,
        time: new Date().toISOString(),
        read: false,
      });
      localStorage.setItem("msh_notifications", JSON.stringify(notifList));
    } catch (err) {
      console.warn("[authService] Could not record transaction:", err.message);
    }
  }

  await mockRequest(null, { delay: 700 });
  return { user: newUser, token: `mock.${Date.now()}` };
}

// Corporate Client self-registration — account is created but locked in
// "pending_approval" until an Admin/BA reviews it (see registerAppRoutes §3).
// No token is returned: the client cannot log in yet.
export async function registerClient(payload) {
  const newUser = {
    ...USERS[0],
    ...payload,
    id: `u${Date.now()}`,
    role: ROLES.CLIENT,
    accountStatus: ACCOUNT_STATUS.PENDING_APPROVAL,
  };

  const list = readRegisteredUsers();
  const existingIdx = list.findIndex((u) => u.email && u.email.toLowerCase() === newUser.email.toLowerCase());
  if (existingIdx > -1) {
    list[existingIdx] = newUser;
  } else {
    list.push(newUser);
  }
  writeRegisteredUsers(list);

  await mockRequest(null, { delay: 700 });
  return { user: newUser };
}

// ---- Admin-side: Client approval queue ----

export async function getPendingClients() {
  const list = readRegisteredUsers();
  await mockRequest(null, { delay: 300 });
  return list.filter((u) => u.role === ROLES.CLIENT && u.accountStatus === ACCOUNT_STATUS.PENDING_APPROVAL);
}

export async function setClientApproval(userId, approve) {
  const list = readRegisteredUsers();
  const updated = list.map((u) =>
    u.id === userId ? { ...u, accountStatus: approve ? ACCOUNT_STATUS.ACTIVE : ACCOUNT_STATUS.REJECTED } : u
  );
  writeRegisteredUsers(updated);
  await mockRequest(null, { delay: 400 });
  return { userId, accountStatus: approve ? ACCOUNT_STATUS.ACTIVE : ACCOUNT_STATUS.REJECTED };
}

// ---- Admin-side: internal staff invites (Trainer, Developer, Lead Gen, HR, BA, Admin) ----
// These roles are never self-registered. Admin creates an "invited" account here;
// in a real backend this sends an emailed, single-use, expiring signup link.

export async function inviteStaffMember({ name, email, role }) {
  const newUser = {
    id: `u${Date.now()}`,
    name,
    email,
    role,
    accountStatus: ACCOUNT_STATUS.INVITED,
    avatarColor: "#0D2845",
    invitedAt: new Date().toISOString(),
  };

  const list = readRegisteredUsers();
  list.push(newUser);
  writeRegisteredUsers(list);

  await mockRequest(null, { delay: 500 });
  // Mock invite link — a real backend would email this instead of returning it
  // to the Admin UI. It's a same-origin, relative link so it actually resolves
  // to the /accept-invite screen in this app (no fake external domain).
  const inviteLink = `${window.location.origin}/accept-invite?token=mock.${newUser.id}`;
  return { user: newUser, inviteLink };
}

// Called from the /accept-invite screen. Looks up the invited account by the
// id embedded in the mock token, sets their chosen password, activates the
// account, and (mirroring HR's onboarding step) adds them to the Employees
// roster so HR sees every activated staff member automatically.
export async function acceptInvite(token, password) {
  if (!token || !password) throw new Error("Invalid invite link or missing password.");
  const match = /mock\.(.+)$/.exec(token);
  const userId = match ? match[1] : null;
  if (!userId) throw new Error("This invite link looks invalid.");

  const list = readRegisteredUsers();
  const idx = list.findIndex((u) => u.id === userId);
  if (idx === -1) throw new Error("This invite link is no longer valid.");

  const invitedUser = list[idx];
  if (invitedUser.accountStatus !== ACCOUNT_STATUS.INVITED) {
    throw new Error("This invite has already been used or is no longer pending.");
  }

  const activatedUser = { ...invitedUser, accountStatus: ACCOUNT_STATUS.ACTIVE, password };
  list[idx] = activatedUser;
  writeRegisteredUsers(list);

  // Sync into HR's employee roster so this person shows up there without
  // HR having to do anything manually.
  try {
    const rawEmployees = localStorage.getItem("msh_employees");
    const employees = rawEmployees ? JSON.parse(rawEmployees) : [];
    const roleLabel = {
      [ROLES.TRAINER]: "Trainer / PM",
      [ROLES.DEVELOPER]: "Developer",
      [ROLES.LEAD_GENERATOR]: "Lead Generator",
      [ROLES.HR]: "HR Specialist",
      [ROLES.BUSINESS_ANALYST]: "Business Analyst",
      [ROLES.ADMIN]: "System Admin",
    }[activatedUser.role] || activatedUser.role;

    if (!employees.some((e) => e.id === activatedUser.id)) {
      employees.push({
        id: activatedUser.id,
        name: activatedUser.name,
        role: roleLabel,
        type: "Internal Staff",
        status: "Active",
        joinDate: new Date().toISOString().slice(0, 10),
        leaveBalance: 12,
        attendance: 100,
      });
      localStorage.setItem("msh_employees", JSON.stringify(employees));
    }
  } catch (err) {
    console.warn("[authService] Could not sync invited staff into Employees:", err.message);
  }

  const token2 = `mock.${btoa(activatedUser.id)}.${Date.now()}`;
  await mockRequest(null, { delay: 600 });
  return { user: activatedUser, token: token2 };
}

// NOTE: there is no real email service wired into this frontend — a genuine
// "reset link" can't be sent. Previously this just showed a fake "check
// your inbox" success screen while leaving the stored password completely
// untouched, which meant a locked-out account (wrong password typed after
// the first login set it) had NO way to recover — "Forgot Password" was a
// dead end dressed up as working. Now it actually clears that one account's
// stored password (never the admin default), so the very next login attempt
// for that email is treated as a first login again and whatever password is
// typed then becomes the new one — same "first login sets the password"
// rule used everywhere else in this mock, just re-triggered on purpose.
export async function requestPasswordReset(email) {
  try {
    const clean = (email || "").trim().toLowerCase();
    if (clean && clean !== "admin@moriah.io") {
      const list = readRegisteredUsers();
      const idx = list.findIndex((u) => u.email && u.email.toLowerCase() === clean);
      if (idx > -1) {
        list[idx] = { ...list[idx], password: null };
        writeRegisteredUsers(list);
      }
    }
  } catch (err) {
    console.warn("[authService] Could not reset password for demo account:", err.message);
  }
  await mockRequest(null, { delay: 600 });
  return { message: `If an account exists for ${email}, a reset link has been sent.` };
}

// Wraps localStorage writes so a full/blocked storage quota (e.g. leftover
// data from other apps that used this same origin/port) never crashes the
// login/register flow. Worst case: the session just won't persist on refresh.
function safeSetItem(key, value) {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch (err) {
    console.warn(`[authService] Could not persist "${key}" to localStorage:`, err.message);
    return false;
  }
}

export function persistSession(user, token) {
  safeSetItem("msh_token", token);
  safeSetItem("msh_user", JSON.stringify(user));
}

export function clearSession() {
  try {
    localStorage.removeItem("msh_token");
    localStorage.removeItem("msh_user");
  } catch (err) {
    console.warn("[authService] Could not clear session from localStorage:", err.message);
  }
}

export function getPersistedUser() {
  try {
    const raw = localStorage.getItem("msh_user");
    if (!raw) return null;
    const sessionUser = JSON.parse(raw);

    if (sessionUser && sessionUser.email) {
      const list = readRegisteredUsers();
      const dbUser = list.find((u) => u.email.toLowerCase() === sessionUser.email.toLowerCase());
      if (dbUser) {
        const syncedUser = { ...sessionUser, ...dbUser };
        localStorage.setItem("msh_user", JSON.stringify(syncedUser));
        return syncedUser;
      }
    }
    return sessionUser;
  } catch {
    return null;
  }
}