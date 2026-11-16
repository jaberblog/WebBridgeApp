// لایه‌ی کمکی روی پل Android — اگر صفحه بیرون از برنامه (مرورگر) باز شد هم خطا نمی‌دهد
(function () {
  var inApp = typeof Android !== 'undefined';

  window.App = {
    inApp: inApp,

    // نمایش Toast کوتاه
    toast: function (msg) {
      inApp ? Android.showToast(String(msg)) : console.log('[toast]', msg);
    },

    // نمایش Toast بلند
    toastLong: function (msg) {
      inApp ? Android.showLongToast(String(msg)) : console.log('[toast-long]', msg);
    },

    // صفحه اصلی => خروج از برنامه | صفحه دیگر => برگشت به عقب
    exitOrBack: function () {
      inApp ? Android.exitOrBack() : history.back();
    },

    // ارسال نوتیفیکیشن
    notify: function (title, text) {
      inApp ? Android.sendNotification(String(title), String(text))
            : console.log('[notify]', title, text);
    },

    // ارسال پیامک (متن بلند خودکار چندبخشی می‌شود)
    sendSms: function (phone, text) {
      inApp ? Android.sendSms(String(phone), String(text))
            : console.log('[sms]', phone, text);
    },

    // نتیجه‌ی ارسال پیامک: App.onSmsResult = function (phone, ok, message) { ... }
    onSmsResult: null
  };

  // این تابع را کاتلین صدا می‌زند
  window.onSmsResult = function (phone, ok, message) {
    if (typeof window.App.onSmsResult === 'function') {
      window.App.onSmsResult(phone, ok, message);
    }
  };
})();
