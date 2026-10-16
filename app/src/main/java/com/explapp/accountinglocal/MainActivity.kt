package com.explapp.accountinglocal

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import java.time.LocalDate

class MainActivity:Activity(){
 private lateinit var db:LedgerDb
 private lateinit var root:LinearLayout
 private val today get()=LocalDate.now().toString()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=LedgerDb(this);home()}
 private fun start(title:String){root=LinearLayout(this).apply{orientation=1;setPadding(25,30,25,20);setBackgroundColor(Color.rgb(247,249,252))};setContentView(ScrollView(this).apply{addView(root)});heading(title);button("↩ الرئيسية"){home()}}
 private fun heading(t:String){root.addView(TextView(this).apply{text=t;textSize=24f;setTextColor(Color.rgb(18,51,85));setPadding(0,12,0,22)})}
 private fun button(t:String,action:()->Unit){root.addView(Button(this).apply{text=t;isAllCaps=false;setOnClickListener{try{action()}catch(e:Exception){show(e.message?:"حدث خطأ")}}})}
 private fun field(hint:String,value:String=""):EditText=EditText(this).apply{setText(value);this.hint=hint;setSingleLine(true);root.addView(this)}
 private fun label(t:String){root.addView(TextView(this).apply{text=t;textSize=16f;setPadding(0,8,0,8);setTextColor(Color.DKGRAY)})}
 private fun show(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
 private fun chooseAccounts():Pair<Spinner,List<Map<String,String>>>{val data=db.accounts();val spinner=Spinner(this);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,data.map{it["code"]+" - "+it["name"]});root.addView(spinner);return spinner to data}
 private fun index(p:Pair<Spinner,List<Map<String,String>>>):Long=p.second[p.first.selectedItemPosition].getValue("id").toLong()
 private fun home(){start("نظام المحاسب | System for Accountant");label("الشركة: "+db.company().ifBlank{"غير محددة"});val d=db.dashboard();label("الإيرادات: "+d["revenue"]+" | المصروفات: "+d["expense"]);button("شجرة الحسابات"){accounts()};button("قيد يومية"){entry()};button("سند قبض"){voucher("receipt")};button("سند صرف"){voucher("payment")};button("دفتر اليومية"){list("دفتر اليومية",db.journal())};button("ميزان المراجعة"){list("ميزان المراجعة",db.trial())};button("كشف حساب"){statement()};button("إعدادات الشركة"){settings()}}
 private fun accounts(){start("دليل الحسابات");db.accounts().forEach{label(it["code"]+" — "+it["name"])}}
 private fun entry(){start("قيد يومية جديد");val date=field("التاريخ yyyy-MM-dd",today);val desc=field("البيان");label("الحساب المدين");val debitAcc=chooseAccounts();val debit=field("مبلغ المدين");label("الحساب الدائن");val creditAcc=chooseAccounts();val credit=field("مبلغ الدائن");button("حفظ القيد"){val code=db.entry(date.text.toString(),desc.text.toString(),listOf(Triple(index(debitAcc),debit.text.toString().toDouble(),0.0),Triple(index(creditAcc),0.0,credit.text.toString().toDouble())));show("تم حفظ القيد $code");home()}}
 private fun voucher(type:String){start(if(type=="receipt")"سند قبض" else "سند صرف");val date=field("التاريخ yyyy-MM-dd",today);val desc=field("البيان");label("حساب النقدية / البنك");val money=chooseAccounts();label("الحساب المقابل");val other=chooseAccounts();val amount=field("المبلغ");button("حفظ السند"){val code=db.voucher(type,date.text.toString(),index(money),index(other),amount.text.toString().toDouble(),desc.text.toString());show("تم حفظ السند $code");home()}}
 private fun list(title:String,rows:List<Map<String,String>>){start(title);if(rows.isEmpty())label("لا توجد بيانات");rows.forEach{label(it.entries.joinToString(" | "){v->v.key+": "+v.value})}}
 private fun statement(){start("كشف حساب");val p=chooseAccounts();val from=field("من yyyy-MM-dd",today.take(4)+"-01-01");val to=field("إلى yyyy-MM-dd",today);button("عرض"){val id=index(p);val rows=db.read("SELECT e.entry_date,e.entry_code,e.description,l.debit,l.credit FROM journal_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.account_id=? AND e.entry_date BETWEEN ? AND ? ORDER BY e.entry_date,e.entry_number",arrayOf(id.toString(),from.text.toString(),to.text.toString()));list("كشف الحساب",rows)}}
 private fun settings(){start("اسم الشركة");val name=field("اسم الشركة",db.company());button("حفظ"){db.company(name.text.toString());home()}}
}
