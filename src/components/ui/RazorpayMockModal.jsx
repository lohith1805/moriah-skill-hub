import { useState, useEffect } from "react";
import { X, ArrowLeft, CreditCard, Wallet, Globe, Lock, ShieldCheck, CheckCircle2, AlertCircle, Smartphone } from "lucide-react";
import Button from "./Button";
import { Input } from "./FormField";
import { useToast } from "../../context/ToastContext";

const BANKS = [
  { id: "sbi", name: "State Bank of India", color: "bg-[#00a2e8]", textColor: "text-white", logoLetter: "S" },
  { id: "hdfc", name: "HDFC Bank", color: "bg-[#004B87]", textColor: "text-white", logoLetter: "H" },
  { id: "icici", name: "ICICI Bank", color: "bg-[#f27022]", textColor: "text-white", logoLetter: "I" },
  { id: "axis", name: "Axis Bank", color: "bg-[#97144d]", textColor: "text-white", logoLetter: "A" },
  { id: "kotak", name: "Kotak Mahindra Bank", color: "bg-[#e61a22]", textColor: "text-white", logoLetter: "K" },
];

export default function RazorpayMockModal({
  isOpen,
  onClose,
  onSuccess,
  amount,
  planName = "Subscription",
  userName = "",
  userEmail = "",
  userPhone = "",
  gateway = "Razorpay",
}) {
  const { notify } = useToast();
  const [showConfirmCancel, setShowConfirmCancel] = useState(false);
  
  // Navigation states: 'options', 'card', 'otp', 'netbanking', 'netbanking-login', 'netbanking-confirm', 'upi', 'upi-verify', 'success'
  const [step, setStep] = useState("options"); 
  
  // Card details
  const [cardNumber, setCardNumber] = useState("6527 6589 0000 1005");
  const [expiry, setExpiry] = useState("12/29");
  const [cvv, setCvv] = useState("123");
  const [cardName, setCardName] = useState(userName || "John Doe");
  const [otp, setOtp] = useState("");
  
  // Netbanking details
  const [selectedBank, setSelectedBank] = useState(null);
  const [bankUser, setBankUser] = useState("");
  const [bankPass, setBankPass] = useState("");
  
  // UPI details
  const [upiId, setUpiId] = useState("");

  // Timers
  const [resendTimer, setResendTimer] = useState(30);
  const [timeoutTimer, setTimeoutTimer] = useState(90); 
  const [isPaying, setIsPaying] = useState(false);

  useEffect(() => {
    let interval;
    if (isOpen && ["otp", "netbanking-login", "netbanking-confirm", "upi-verify"].includes(step)) {
      interval = setInterval(() => {
        setResendTimer((prev) => (prev > 0 ? prev - 1 : 0));
        setTimeoutTimer((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isOpen, step]);

  // Reset state when modal opens
  useEffect(() => {
    if (isOpen) {
      setStep(gateway === "Stripe" ? "stripe-card" : "options");
      setOtp("");
      setBankUser("");
      setBankPass("");
      setSelectedBank(null);
      setUpiId(userPhone ? `${userPhone}@upi` : "8790162843@upi");
      setResendTimer(30);
      setTimeoutTimer(90);
      setIsPaying(false);
    }
  }, [isOpen, userPhone, gateway]);

  if (!isOpen) return null;

  const formattedAmount = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);

  const formatTimeout = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")} mins`;
  };

  const handleCardPaymentSubmit = (e) => {
    e.preventDefault();
    if (!cardNumber || !expiry || !cvv || !cardName) {
      notify("Please fill all card details", { type: "warning" });
      return;
    }
    setIsPaying(true);
    setTimeout(() => {
      setIsPaying(false);
      setStep("otp");
    }, 1000);
  };

  const handleOtpSubmit = (e) => {
    e.preventDefault();
    if (!otp || otp.length < 4) {
      notify("Please enter a valid OTP code", { type: "warning" });
      return;
    }
    triggerPaymentSuccess();
  };

  const handleNetbankingSubmit = (e) => {
    e.preventDefault();
    if (!bankUser || !bankPass) {
      notify("Please enter User ID and Password", { type: "warning" });
      return;
    }
    setIsPaying(true);
    setTimeout(() => {
      setIsPaying(false);
      setStep("netbanking-confirm");
    }, 800);
  };

  const handleNetbankingConfirmSubmit = (e) => {
    e.preventDefault();
    triggerPaymentSuccess();
  };

  const handleUpiSubmit = (e) => {
    e.preventDefault();
    if (!upiId) {
      notify("Please enter a valid UPI ID", { type: "warning" });
      return;
    }
    if (!upiId.includes("@")) {
      notify("UPI ID must contain '@'", { type: "warning" });
      return;
    }
    setIsPaying(true);
    setTimeout(() => {
      setIsPaying(false);
      setStep("upi-verify");
    }, 800);
  };

  const triggerPaymentSuccess = () => {
    setIsPaying(true);
    setTimeout(() => {
      setIsPaying(false);
      setStep("success");
      setTimeout(() => {
        onSuccess({
          razorpay_payment_id: gateway === "Stripe"
            ? `stripe_pi_${Math.random().toString(36).substring(2, 11)}`
            : `rzp_${Math.random().toString(36).substring(2, 11)}`,
        });
      }, 1200);
    }, 1200);
  };

  const handleCancel = () => {
    setShowConfirmCancel(true);
  };

  return (
    <div className="fixed inset-0 z-[99999] flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
      {/* Outer Checkout Window */}
      <div className="relative flex flex-col md:flex-row w-full max-w-[850px] h-[550px] bg-slate-900 text-white rounded-2xl shadow-2xl overflow-hidden border border-slate-700/50">
        
        {/* Top-right General Close Button (Except on success page) */}
        {step !== "success" && (
          <button
            onClick={handleCancel}
            className="absolute top-4 right-4 z-50 p-1 rounded-full text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title="Cancel Payment"
          >
            <X size={20} />
          </button>
        )}

        {/* Left Panel: Price & Merchant Details */}
        <div className={"w-full md:w-5/12 p-8 flex flex-col justify-between border-b md:border-b-0 md:border-r border-slate-800/80 " + (gateway === "Stripe" ? "bg-[#4F46E5]" : "bg-[#0D2845]")}>
          <div>
            <div className="flex items-center gap-3 mb-8">
              <div className="w-10 h-10 rounded-lg bg-gold-400 flex items-center justify-center font-display text-lg font-bold text-primary-950 shadow-inner">
                M
              </div>
              <div>
                <h3 className="font-display font-bold text-white text-base leading-tight">Moriah Skill Hub</h3>
                <p className="text-xs text-slate-400">Secured payment gateway</p>
              </div>
            </div>

            <div className="mt-12 bg-slate-900/40 border border-slate-800/50 rounded-xl p-5">
              <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold block mb-1">Price Summary</span>
              <span className="text-3xl font-extrabold text-white">{formattedAmount}</span>
              <p className="text-xs text-gold-300 mt-2 font-medium">{planName}</p>
            </div>
          </div>

          <div className="mt-8 flex flex-col gap-4">
            <div className="flex items-center gap-2.5 text-xs text-slate-300">
              <div className="p-1 rounded bg-slate-800/60">
                <Globe size={14} className="text-slate-400" />
              </div>
              <span>Using as <strong className="text-white">{userPhone || "+91 99999 99999"}</strong></span>
            </div>
            
            <div className="flex items-center gap-1.5 text-[10px] text-slate-400 border-t border-slate-800/80 pt-4">
              <Lock size={12} className="text-slate-500" />
              <span>Secured by <strong className="text-slate-300">{gateway}</strong></span>
            </div>
          </div>
        </div>

        {/* Right Panel: Content / Action Areas */}
        <div className="w-full md:w-7/12 bg-slate-950 p-8 flex flex-col justify-between overflow-y-auto relative">
          
          {/* Main Views */}
          {step === "options" && (
            <div className="flex-1 flex flex-col justify-center">
              <h2 className="text-xl font-bold font-display text-white mb-1">Payment Options</h2>
              <p className="text-xs text-slate-400 mb-6">Select a convenient payment method below</p>

              <div className="flex flex-col gap-3">
                {/* Cards option */}
                <button
                  type="button"
                  onClick={() => setStep("card")}
                  className="flex items-center justify-between p-4 rounded-xl border border-slate-800 bg-slate-900/50 hover:bg-slate-900 hover:border-slate-700 transition-all group text-left"
                >
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 rounded-lg bg-primary-950 flex items-center justify-center border border-primary-800 text-gold-400">
                      <CreditCard size={20} />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-white group-hover:text-gold-300 transition-colors">Cards</h4>
                      <p className="text-xs text-slate-400">Visa, Mastercard, RuPay, Maestro</p>
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <span className="text-[10px] bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">RuPay</span>
                    <span className="text-[10px] bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">Visa</span>
                  </div>
                </button>

                {/* Netbanking Option */}
                <button
                  type="button"
                  onClick={() => setStep("netbanking")}
                  className="flex items-center justify-between p-4 rounded-xl border border-slate-800 bg-slate-900/50 hover:bg-slate-900 hover:border-slate-700 transition-all group text-left"
                >
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 rounded-lg bg-primary-950 flex items-center justify-center border border-primary-800 text-gold-400">
                      <Globe size={20} />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-white group-hover:text-gold-300 transition-colors">Netbanking</h4>
                      <p className="text-xs text-slate-400">All major Indian banks</p>
                    </div>
                  </div>
                  <span className="text-[10px] bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">Popular</span>
                </button>

                {/* UPI Option */}
                <button
                  type="button"
                  onClick={() => setStep("upi")}
                  className="flex items-center justify-between p-4 rounded-xl border border-slate-800 bg-slate-900/50 hover:bg-slate-900 hover:border-slate-700 transition-all group text-left"
                >
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 rounded-lg bg-primary-950 flex items-center justify-center border border-primary-800 text-gold-400">
                      <Smartphone size={20} />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-white group-hover:text-gold-300 transition-colors">UPI</h4>
                      <p className="text-xs text-slate-400">Google Pay, PhonePe, Paytm, BHIM</p>
                    </div>
                  </div>
                  <span className="text-[10px] bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">Instant</span>
                </button>
              </div>
            </div>
          )}

          {/* Cards Input Form */}
          {step === "card" && (
            <form onSubmit={handleCardPaymentSubmit} className="flex-1 flex flex-col justify-between">
              <div>
                <button
                  type="button"
                  onClick={() => setStep("options")}
                  className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white mb-6 group transition-colors"
                >
                  <ArrowLeft size={14} className="group-hover:-translate-x-0.5 transition-transform" />
                  <span>Back to methods</span>
                </button>

                <h2 className="text-xl font-bold font-display text-white mb-5">Card Details</h2>

                <div className="flex flex-col gap-4">
                  <Input
                    label="Card Number"
                    placeholder="6527 6589 0000 1005"
                    value={cardNumber}
                    onChange={(e) => setCardNumber(e.target.value)}
                    required
                  />

                  <div className="grid grid-cols-2 gap-4">
                    <Input
                      label="Expiry (MM/YY)"
                      placeholder="12/29"
                      value={expiry}
                      onChange={(e) => setExpiry(e.target.value)}
                      required
                    />
                    <Input
                      label="CVV"
                      type="password"
                      placeholder="123"
                      maxLength={3}
                      value={cvv}
                      onChange={(e) => setCvv(e.target.value)}
                      required
                    />
                  </div>

                  <Input
                    label="Card Holder Name"
                    placeholder="John Doe"
                    value={cardName}
                    onChange={(e) => setCardName(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="mt-8">
                <Button type="submit" fullWidth loading={isPaying} icon={CreditCard}>
                  Pay {formattedAmount}
                </Button>
              </div>
            </form>
          )}

          {/* Netbanking Bank Selection */}
          {step === "netbanking" && (
            <div className="flex-1 flex flex-col justify-between">
              <div>
                <button
                  type="button"
                  onClick={() => setStep("options")}
                  className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white mb-6 group transition-colors"
                >
                  <ArrowLeft size={14} className="group-hover:-translate-x-0.5 transition-transform" />
                  <span>Back to methods</span>
                </button>

                <h2 className="text-xl font-bold font-display text-white mb-1">Select Bank</h2>
                <p className="text-xs text-slate-400 mb-6">Choose your bank to complete payment via Netbanking</p>

                <div className="grid grid-cols-1 gap-2.5 max-h-[250px] overflow-y-auto pr-1">
                  {BANKS.map((bank) => (
                    <button
                      key={bank.id}
                      type="button"
                      onClick={() => setSelectedBank(bank)}
                      className={`flex items-center justify-between p-3.5 rounded-xl border transition-all text-left ${
                        selectedBank?.id === bank.id
                          ? "border-gold-400 bg-slate-900 text-white"
                          : "border-slate-800 bg-slate-900/40 text-slate-300 hover:border-slate-700 hover:bg-slate-900/70"
                      }`}
                    >
                      <div className="flex items-center gap-3.5">
                        <div className={`w-8 h-8 rounded-full ${bank.color} flex items-center justify-center font-bold text-sm ${bank.textColor}`}>
                          {bank.logoLetter}
                        </div>
                        <span className="text-sm font-semibold">{bank.name}</span>
                      </div>
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${selectedBank?.id === bank.id ? "border-gold-400" : "border-slate-700"}`}>
                        {selectedBank?.id === bank.id && <div className="w-2.5 h-2.5 rounded-full bg-gold-400" />}
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-8">
                <Button
                  type="button"
                  fullWidth
                  disabled={!selectedBank}
                  onClick={() => setStep("netbanking-login")}
                  icon={Globe}
                >
                  Pay {formattedAmount} with {selectedBank?.name || "Selected Bank"}
                </Button>
              </div>
            </div>
          )}

          {/* UPI ID Input Form */}
          {step === "upi" && (
            <form onSubmit={handleUpiSubmit} className="flex-1 flex flex-col justify-between">
              <div>
                <button
                  type="button"
                  onClick={() => setStep("options")}
                  className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white mb-6 group transition-colors"
                >
                  <ArrowLeft size={14} className="group-hover:-translate-x-0.5 transition-transform" />
                  <span>Back to methods</span>
                </button>

                <h2 className="text-xl font-bold font-display text-white mb-1">Enter UPI ID</h2>
                <p className="text-xs text-slate-400 mb-6">Enter your UPI ID / VPA to pay securely</p>

                <div className="flex flex-col gap-4">
                  <Input
                    label="UPI ID / VPA"
                    placeholder="e.g. success@upi or mobile@ybl"
                    value={upiId}
                    onChange={(e) => setUpiId(e.target.value)}
                    required
                    autoFocus
                  />
                  
                  <div className="bg-slate-900/40 border border-slate-800/60 rounded-xl p-4 flex flex-col gap-2">
                    <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">Common Handles</span>
                    <div className="flex flex-wrap gap-2">
                      {["@okaxis", "@okhdfcbank", "@okicici", "@okpaytm", "@ybl", "@upi"].map((handle) => (
                        <button
                          key={handle}
                          type="button"
                          onClick={() => {
                            const prefix = upiId.includes("@") ? upiId.split("@")[0] : upiId || (userPhone || "8790162843");
                            setUpiId(prefix + handle);
                          }}
                          className="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1 rounded transition-colors"
                        >
                          {handle}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-8">
                <Button type="submit" fullWidth loading={isPaying} icon={Smartphone}>
                  Pay {formattedAmount}
                </Button>
              </div>
            </form>
          )}

          {/* Cards OTP Screen: Bank simulator */}
          {step === "otp" && (
            <div className="absolute inset-0 bg-slate-950 p-6 flex flex-col items-center justify-center z-10 animate-scale-up">
              
              {/* Timeout Indicator */}
              <div className="absolute top-4 left-6 text-xs text-slate-400 font-medium">
                Timeout in <span className="text-gold-300 font-semibold">{formatTimeout(timeoutTimer)}</span>
              </div>

              {/* HDFC Bank simulated card container */}
              <div className="relative w-full max-w-[420px] bg-white text-slate-900 rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-fade-in">
                
                {/* Close/Cancel Button inside the Bank OTP card container */}
                <button
                  type="button"
                  onClick={handleCancel}
                  className="absolute top-3 right-3 z-20 p-1.5 rounded-full text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors"
                  title="Cancel Payment"
                >
                  <X size={18} />
                </button>

                {/* HDFC / RuPay themed header */}
                <div className="bg-[#004B87] px-5 py-3 flex items-center justify-between text-white border-b border-slate-100 shadow-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded bg-red-600 flex items-center justify-center font-bold text-xs text-white">
                      H
                    </div>
                    <span className="font-semibold text-xs tracking-wider">HDFC BANK</span>
                  </div>
                  <div className="text-xs font-bold italic tracking-widest text-[#FF5F00] bg-white px-2 py-0.5 rounded shadow-sm">
                    RuPay▸
                  </div>
                </div>

                {/* Bank simulator OTP form */}
                <form onSubmit={handleOtpSubmit} className="p-6 flex flex-col gap-5">
                  <div className="text-center">
                    <h3 className="font-display font-bold text-slate-900 text-base leading-snug">Enter OTP to complete payment</h3>
                    <p className="text-xs text-slate-500 mt-2 px-4 leading-normal">
                      Enter OTP sent to the number linked to your card ending with <strong className="text-slate-800 font-semibold">{cardNumber.slice(-4) || "1005"}</strong>
                    </p>
                  </div>

                  <div>
                    <input
                      type="text"
                      placeholder="Enter OTP"
                      value={otp}
                      onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                      className="w-full text-center tracking-[0.2em] font-mono text-base font-bold border border-slate-300 rounded-lg py-2.5 px-4 focus:ring-2 focus:ring-[#004B87] focus:border-[#004B87] outline-none text-slate-800 placeholder:tracking-normal placeholder:font-sans placeholder:text-slate-400 transition-all"
                      maxLength={6}
                      required
                      autoFocus
                    />
                  </div>

                  <div className="flex items-center justify-between text-xs text-slate-500 font-medium border-t border-slate-100 pt-4">
                    <a
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        notify("Redirecting to Bank's secured page (Simulated)...", { type: "info" });
                      }}
                      className="text-blue-600 hover:text-blue-800 underline transition-colors"
                    >
                      Pay on bank's page
                    </a>
                    {resendTimer > 0 ? (
                      <span className="text-slate-400">Resend OTP in {resendTimer}s</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setResendTimer(30);
                          notify("OTP resent successfully to registered phone number", { type: "success" });
                        }}
                        className="text-blue-600 hover:text-blue-800 underline font-semibold transition-colors"
                      >
                        Resend OTP
                      </button>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={isPaying}
                    className="w-full bg-[#1A1A1A] hover:bg-black text-white py-3 rounded-lg font-semibold text-sm transition-all shadow hover:shadow-md disabled:opacity-75 flex items-center justify-center gap-2"
                  >
                    {isPaying ? "Processing..." : "Continue"}
                  </button>

                  <div className="flex items-center justify-center gap-1.5 text-[10px] text-slate-400 border-t border-slate-100 pt-3">
                    <span className="font-medium">Secured by</span>
                    <strong className="text-slate-500 tracking-wide font-bold">Razorpay</strong>
                  </div>
                </form>
              </div>

              {/* Cancel Button below the Bank OTP card container */}
              <button
                type="button"
                onClick={handleCancel}
                className="mt-6 flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
              >
                <X size={14} />
                <span>Cancel Payment</span>
              </button>
            </div>
          )}

          {/* Netbanking Login Screen: Bank simulator */}
          {step === "netbanking-login" && (
            <div className="absolute inset-0 bg-slate-950 p-6 flex flex-col items-center justify-center z-10 animate-scale-up">
              
              {/* Timeout Indicator */}
              <div className="absolute top-4 left-6 text-xs text-slate-400 font-medium">
                Timeout in <span className="text-gold-300 font-semibold">{formatTimeout(timeoutTimer)}</span>
              </div>

              {/* Netbanking card container */}
              <div className="relative w-full max-w-[420px] bg-white text-slate-900 rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-fade-in">
                
                {/* Close/Cancel Button inside the Bank Login card container */}
                <button
                  type="button"
                  onClick={handleCancel}
                  className="absolute top-3 right-3 z-20 p-1.5 rounded-full text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors"
                  title="Cancel Payment"
                >
                  <X size={18} />
                </button>

                {/* Dynamic Bank themed header */}
                <div className={`px-5 py-3 flex items-center gap-2.5 text-white border-b border-slate-100 shadow-sm ${selectedBank?.color || "bg-slate-800"}`}>
                  <div className="w-6 h-6 rounded bg-white flex items-center justify-center font-bold text-xs text-slate-900 shadow-inner">
                    {selectedBank?.logoLetter || "B"}
                  </div>
                  <span className="font-bold text-xs tracking-wider uppercase">{selectedBank?.name || "Netbanking Simulator"}</span>
                </div>

                {/* Netbanking Login Form */}
                <form onSubmit={handleNetbankingSubmit} className="p-6 flex flex-col gap-4">
                  <div className="text-center mb-2">
                    <h3 className="font-display font-bold text-slate-800 text-sm">NetBanking Secure Login</h3>
                    <p className="text-[11px] text-slate-500 mt-1 leading-normal flex items-center gap-1 justify-center">
                      <AlertCircle size={12} className="text-slate-400" />
                      <span>Simulated Bank Page. Do not enter real credentials.</span>
                    </p>
                  </div>

                  <div className="flex flex-col gap-3">
                    <div className="flex flex-col gap-1 text-left">
                      <label className="text-[10px] font-semibold text-slate-600 uppercase tracking-wide">Customer ID / User ID</label>
                      <input
                        type="text"
                        placeholder="Enter Bank Customer ID"
                        value={bankUser}
                        onChange={(e) => setBankUser(e.target.value)}
                        className="w-full text-sm border border-slate-300 rounded-lg py-2 px-3 focus:ring-2 focus:ring-blue-600 focus:border-blue-600 outline-none text-slate-800"
                        required
                        autoFocus
                      />
                    </div>
                    
                    <div className="flex flex-col gap-1 text-left">
                      <label className="text-[10px] font-semibold text-slate-600 uppercase tracking-wide">Password / PIN</label>
                      <input
                        type="password"
                        placeholder="••••••••"
                        value={bankPass}
                        onChange={(e) => setBankPass(e.target.value)}
                        className="w-full text-sm border border-slate-300 rounded-lg py-2 px-3 focus:ring-2 focus:ring-blue-600 focus:border-blue-600 outline-none text-slate-800"
                        required
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={isPaying}
                    className="w-full mt-2 bg-[#1A1A1A] hover:bg-black text-white py-2.5 rounded-lg font-semibold text-sm transition-all shadow hover:shadow-md disabled:opacity-75"
                  >
                    {isPaying ? "Verifying..." : "Login"}
                  </button>

                  <div className="flex items-center justify-center gap-1.5 text-[10px] text-slate-400 border-t border-slate-100 pt-3">
                    <span className="font-medium">Secured by</span>
                    <strong className="text-slate-500 tracking-wide font-bold">Razorpay</strong>
                  </div>
                </form>
              </div>

              {/* Cancel Button below the Bank Login card */}
              <button
                type="button"
                onClick={handleCancel}
                className="mt-6 flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
              >
                <X size={14} />
                <span>Cancel Payment</span>
              </button>
            </div>
          )}

          {/* Netbanking Confirm Screen: Bank simulator */}
          {step === "netbanking-confirm" && (
            <div className="absolute inset-0 bg-slate-950 p-6 flex flex-col items-center justify-center z-10 animate-scale-up">
              
              {/* Timeout Indicator */}
              <div className="absolute top-4 left-6 text-xs text-slate-400 font-medium">
                Timeout in <span className="text-gold-300 font-semibold">{formatTimeout(timeoutTimer)}</span>
              </div>

              {/* Confirm container */}
              <div className="relative w-full max-w-[420px] bg-white text-slate-900 rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-fade-in">
                
                {/* Close/Cancel Button inside the Bank Confirm card */}
                <button
                  type="button"
                  onClick={handleCancel}
                  className="absolute top-3 right-3 z-20 p-1.5 rounded-full text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors"
                  title="Cancel Payment"
                >
                  <X size={18} />
                </button>

                {/* Bank Header */}
                <div className={`px-5 py-3 flex items-center gap-2.5 text-white border-b border-slate-100 shadow-sm ${selectedBank?.color || "bg-slate-800"}`}>
                  <div className="w-6 h-6 rounded bg-white flex items-center justify-center font-bold text-xs text-slate-900 shadow-inner">
                    {selectedBank?.logoLetter || "B"}
                  </div>
                  <span className="font-bold text-xs tracking-wider uppercase">{selectedBank?.name || "Netbanking Simulator"}</span>
                </div>

                {/* Netbanking Confirmation details */}
                <form onSubmit={handleNetbankingConfirmSubmit} className="p-6 flex flex-col gap-4">
                  <div className="text-center border-b border-slate-100 pb-3">
                    <h3 className="font-display font-bold text-slate-800 text-sm">Confirm Transaction</h3>
                    <p className="text-[11px] text-slate-500 mt-1">Please authorize the following transfer</p>
                  </div>

                  <div className="bg-slate-50 rounded-xl p-4 flex flex-col gap-2.5 text-xs border border-slate-100">
                    <div className="flex justify-between">
                      <span className="text-slate-500">Merchant:</span>
                      <strong className="text-slate-800 font-semibold">Moriah Skill Hub</strong>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Amount:</span>
                      <strong className="text-slate-800 font-bold text-sm text-[#004B87]">{formattedAmount}</strong>
                    </div>
                    <div className="flex justify-between border-t border-slate-200/50 pt-2">
                      <span className="text-slate-500">From Account:</span>
                      <strong className="text-slate-800 font-medium">Netbanking Account (xxxx3920)</strong>
                    </div>
                  </div>

                  <div className="flex gap-3 mt-2">
                    <button
                      type="button"
                      onClick={handleCancel}
                      className="w-1/2 border border-slate-300 hover:bg-slate-50 text-slate-700 py-2.5 rounded-lg font-semibold text-xs transition-all"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isPaying}
                      className="w-1/2 bg-[#004B87] hover:bg-[#003966] text-white py-2.5 rounded-lg font-semibold text-xs transition-all shadow disabled:opacity-75"
                    >
                      {isPaying ? "Processing..." : "Confirm & Pay"}
                    </button>
                  </div>

                  <div className="flex items-center justify-center gap-1.5 text-[10px] text-slate-400 border-t border-slate-100 pt-3">
                    <span className="font-medium">Secured by</span>
                    <strong className="text-slate-500 tracking-wide font-bold">Razorpay</strong>
                  </div>
                </form>
              </div>

              {/* Cancel Button below the Bank Confirm card */}
              <button
                type="button"
                onClick={handleCancel}
                className="mt-6 flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
              >
                <X size={14} />
                <span>Cancel Payment</span>
              </button>
            </div>
          )}

          {/* UPI Verify Screen: Phone/UPI simulator */}
          {step === "upi-verify" && (
            <div className="absolute inset-0 bg-slate-950 p-6 flex flex-col items-center justify-center z-10 animate-scale-up">
              
              {/* Timeout Indicator */}
              <div className="absolute top-4 left-6 text-xs text-slate-400 font-medium">
                Timeout in <span className="text-gold-300 font-semibold">{formatTimeout(timeoutTimer)}</span>
              </div>

              {/* UPI simulated card container */}
              <div className="relative w-full max-w-[420px] bg-white text-slate-900 rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-fade-in">
                
                {/* Close/Cancel Button inside the UPI card */}
                <button
                  type="button"
                  onClick={handleCancel}
                  className="absolute top-3 right-3 z-20 p-1.5 rounded-full text-slate-400 hover:text-slate-800 hover:bg-slate-100 transition-colors"
                  title="Cancel Payment"
                >
                  <X size={18} />
                </button>

                {/* UPI themed header */}
                <div className="bg-[#5f259f] px-5 py-3 flex items-center justify-between text-white border-b border-slate-100 shadow-sm">
                  <div className="flex items-center gap-2">
                    <Smartphone size={16} />
                    <span className="font-semibold text-xs tracking-wider">UPI PAYMENT REQUEST</span>
                  </div>
                  <span className="text-xs font-bold italic tracking-widest text-[#FF5F00] bg-white px-2 py-0.5 rounded shadow-sm">
                    UPI▸
                  </span>
                </div>

                {/* UPI simulator instructions */}
                <div className="p-6 flex flex-col gap-5">
                  <div className="text-center">
                    <h3 className="font-display font-bold text-slate-900 text-base leading-snug">Approve Payment Request</h3>
                    <p className="text-xs text-slate-500 mt-2.5 px-4 leading-normal">
                      We have sent a simulated payment request to <strong className="text-slate-800 font-semibold">{upiId}</strong>
                    </p>
                    <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                      Please open your UPI app (Google Pay, PhonePe, Paytm, etc.) to approve the transaction.
                    </p>
                  </div>

                  <div className="flex flex-col items-center justify-center p-4 bg-slate-50 rounded-xl border border-slate-100">
                    <div className="w-10 h-10 rounded-full border-4 border-[#5f259f] border-t-transparent animate-spin mb-3" />
                    <span className="text-xs text-slate-500 font-medium">Waiting for authorization...</span>
                  </div>

                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={handleCancel}
                      className="w-1/2 border border-slate-300 hover:bg-slate-50 text-slate-700 py-2.5 rounded-lg font-semibold text-xs transition-all"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={triggerPaymentSuccess}
                      disabled={isPaying}
                      className="w-1/2 bg-[#5f259f] hover:bg-[#4d1d82] text-white py-2.5 rounded-lg font-semibold text-xs transition-all shadow disabled:opacity-75"
                    >
                      {isPaying ? "Processing..." : "Simulate Success"}
                    </button>
                  </div>

                  <div className="flex items-center justify-center gap-1.5 text-[10px] text-slate-400 border-t border-slate-100 pt-3">
                    <span className="font-medium">Secured by</span>
                    <strong className="text-slate-500 tracking-wide font-bold">Razorpay</strong>
                  </div>
                </div>
              </div>

              {/* Cancel Button below the card */}
              <button
                type="button"
                onClick={handleCancel}
                className="mt-6 flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
              >
                <X size={14} />
                <span>Cancel Payment</span>
              </button>
            </div>
          )}

          {step === "stripe-card" && (
            <form onSubmit={(e) => { e.preventDefault(); triggerPaymentSuccess(); }} className="flex-1 flex flex-col justify-between">
              <div>
                <h2 className="text-xl font-bold font-display text-white mb-1">Stripe Checkout</h2>
                <p className="text-xs text-slate-400 mb-6">Enter your card details to complete payment</p>

                <div className="flex flex-col gap-4 text-left">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-slate-400">Email Address</label>
                    <input
                      type="email"
                      value={userEmail}
                      disabled
                      className="w-full rounded-lg border border-slate-800 bg-slate-900/50 px-3.5 h-10 text-sm text-slate-400 focus:outline-none"
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-slate-400 font-sans">Card Information</label>
                    <div className="flex flex-col rounded-lg border border-slate-800 bg-slate-900/50 divide-y divide-slate-800">
                      <input
                        type="text"
                        placeholder="Card Number"
                        value={cardNumber}
                        onChange={(e) => setCardNumber(e.target.value)}
                        className="w-full bg-transparent px-3.5 h-10 text-sm text-white placeholder:text-slate-500 focus:outline-none"
                        required
                      />
                      <div className="flex divide-x divide-slate-800">
                        <input
                          type="text"
                          placeholder="MM/YY"
                          value={expiry}
                          onChange={(e) => setExpiry(e.target.value)}
                          className="w-1/2 bg-transparent px-3.5 h-10 text-sm text-white placeholder:text-slate-500 focus:outline-none"
                          required
                        />
                        <input
                          type="password"
                          placeholder="CVC"
                          maxLength={3}
                          value={cvv}
                          onChange={(e) => setCvv(e.target.value)}
                          className="w-1/2 bg-transparent px-3.5 h-10 text-sm text-white placeholder:text-slate-500 focus:outline-none"
                          required
                        />
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-slate-400">Cardholder Name</label>
                    <input
                      type="text"
                      placeholder="Name on card"
                      value={cardName}
                      onChange={(e) => setCardName(e.target.value)}
                      className="w-full rounded-lg border border-slate-800 bg-slate-900/50 px-3.5 h-10 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-slate-700"
                      required
                    />
                  </div>
                </div>
              </div>

              <div className="mt-8">
                <Button type="submit" fullWidth loading={isPaying} className="bg-[#635BFF] hover:bg-[#5b54e0] text-white">
                  Pay {formattedAmount}
                </Button>
              </div>
            </form>
          )}

          {step === "success" && (
            <div className="flex-1 flex flex-col items-center justify-center text-center animate-scale-up">
              <div className="w-16 h-16 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mb-6 shadow-inner animate-pulse">
                <CheckCircle2 size={36} />
              </div>
              <h2 className="text-2xl font-bold font-display text-white mb-2">Payment Completed</h2>
              <p className="text-sm text-slate-400 max-w-xs leading-relaxed">
                Thank you! Your payment of <strong className="text-white font-semibold">{formattedAmount}</strong> was processed successfully.
              </p>
              <div className="mt-8 flex items-center gap-1.5 text-xs text-slate-500">
                <ShieldCheck size={14} className="text-slate-400" />
                <span>Secure connection closed</span>
              </div>
            </div>
          )}
        </div>

      </div>

      {showConfirmCancel && (
        <div className="fixed inset-0 z-[100000] flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
          <div className="bg-slate-900 border border-slate-700/60 rounded-2xl shadow-2xl p-6 w-full max-w-sm flex flex-col items-center text-center animate-scale-up">
            <div className="w-12 h-12 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 flex items-center justify-center mb-4">
              <AlertCircle size={24} />
            </div>
            
            <h3 className="text-lg font-bold text-white mb-2">Cancel Payment?</h3>
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              Are you sure you want to cancel this payment? Any details entered will not be saved.
            </p>

            <div className="flex w-full gap-3">
              <button
                type="button"
                onClick={() => setShowConfirmCancel(false)}
                className="w-1/2 bg-slate-800 hover:bg-slate-700 text-white py-2.5 rounded-lg text-xs font-semibold transition-colors"
              >
                No, Continue
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowConfirmCancel(false);
                  onClose();
                }}
                className="w-1/2 bg-red-600 hover:bg-red-700 text-white py-2.5 rounded-lg text-xs font-semibold transition-colors"
              >
                Yes, Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
