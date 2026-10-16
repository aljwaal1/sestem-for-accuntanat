package com.explapp.accountinglocal

import android.content.*
import android.database.sqlite.*
import java.time.LocalDate

class LedgerDb(context:Context):SQLiteOpenHelper(context,"accounting.db",null,1){
 override fun onConfigure(db:SQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
 override fun onCreate(db:SQLiteDatabase){
 db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY,code TEXT UNIQUE NOT NULL,name TEXT NOT NULL,level INTEGER NOT NULL,parent_id INTEGER,account_type TEXT NOT NULL,normal_balance TEXT NOT NULL,is_postable INTEGER DEFAULT 1,is_active INTEGER DEFAULT 1)")
 db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY,fiscal_year INTEGER NOT NULL,entry_number INTEGER NOT NULL,entry_code TEXT UNIQUE NOT NULL,entry_date TEXT NOT NULL,description TEXT NOT NULL,UNIQUE(fiscal_year,entry_number))")
 db.execSQL("CREATE TABLE journal_lines(id INTEGER PRIMARY KEY,journal_entry_id INTEGER NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,account_id INTEGER NOT NULL REFERENCES accounts(id),debit REAL NOT NULL CHECK(debit>=0),credit REAL NOT NULL CHECK(credit>=0),memo TEXT)")
 db.execSQL("CREATE TABLE vouchers(id INTEGER PRIMARY KEY,voucher_type TEXT NOT NULL,fiscal_year INTEGER NOT NULL,voucher_number INTEGER NOT NULL,voucher_code TEXT UNIQUE NOT NULL,voucher_date TEXT NOT NULL,method TEXT NOT NULL,money_account_id INTEGER NOT NULL,counterparty_account_id INTEGER NOT NULL,person_name TEXT,amount REAL NOT NULL,description TEXT NOT NULL,journal_entry_id INTEGER NOT NULL,status TEXT DEFAULT 'posted',UNIQUE(voucher_type,fiscal_year,voucher_number))")
 db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
 db.execSQL("INSERT INTO app_settings VALUES('company_name','')")
 val seeds=listOf("1-01-001|الصندوق|asset","1-01-002|البنك|asset","1-02-001|العملاء|asset","1-03-001|المخزون|asset","2-01-001|الموردون|liability","2-02-001|قروض|liability","3-01-001|رأس المال|equity","4-01-001|المبيعات|revenue","4-01-002|إيرادات خدمات|revenue","5-01-001|رواتب وأجور|expense","5-01-002|الإيجار|expense","5-03-001|تكلفة البضاعة|expense")
 seeds.forEach{val p=it.split("|");db.execSQL("INSERT INTO accounts(code,name,level,account_type,normal_balance) VALUES(?,?,3,?,?)",arrayOf(p[0],p[1],p[2],if(p[2]=="asset"||p[2]=="expense")"debit" else "credit"))}
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("Migration required")}
 fun read(sql:String,args:Array<String> = emptyArray()):List<Map<String,String>>=readableDatabase.rawQuery(sql,args).use{c->buildList{while(c.moveToNext())add((0 until c.columnCount).associate{c.getColumnName(it) to (if(c.isNull(it))"" else c.getString(it))})}}
 fun accounts()=read("SELECT id,code,name,account_type FROM accounts WHERE is_postable=1 AND is_active=1 ORDER BY code")
 fun company()=read("SELECT value FROM app_settings WHERE key='company_name'").firstOrNull()?.get("value").orEmpty()
 fun company(value:String){writableDatabase.execSQL("UPDATE app_settings SET value=? WHERE key='company_name'",arrayOf(value))}
 fun entry(date:String,desc:String,lines:List<Triple<Long,Double,Double>>):String{
 LocalDate.parse(date);require(desc.isNotBlank()&&lines.size>=2)
 fun milli(x:Double):Long{require(x.isFinite()&&x>=0);return java.math.BigDecimal.valueOf(x).movePointRight(3).longValueExact()}
 require(lines.sumOf{milli(it.second)}==lines.sumOf{milli(it.third)}&&lines.sumOf{milli(it.second)}>0)
 require(lines.all{(id,d,c)->(d==0.0).xor(c==0.0)&&accounts().any{it["id"]==id.toString()}})
 val db=writableDatabase;db.beginTransaction()
 try{
 val year=date.take(4).toInt()
 val n=db.rawQuery("SELECT COALESCE(MAX(entry_number),0)+1 FROM journal_entries WHERE fiscal_year=?",arrayOf("$year")).use{it.moveToFirst();it.getInt(0)}
 val code="$year-"+n.toString().padStart(4,'0')
 val v=ContentValues().apply{put("fiscal_year",year);put("entry_number",n);put("entry_code",code);put("entry_date",date);put("description",desc)}
 val id=db.insertOrThrow("journal_entries",null,v)
 lines.forEach{(acc,d,c)->db.insertOrThrow("journal_lines",null,ContentValues().apply{put("journal_entry_id",id);put("account_id",acc);put("debit",d);put("credit",c)})}
 db.setTransactionSuccessful();return code
 }finally{db.endTransaction()}
 }
 fun voucher(type:String,date:String,money:Long,other:Long,amount:Double,desc:String):String{
 require(type=="receipt"||type=="payment");require(amount>0&&money!=other)
 val code=entry(date,desc,if(type=="receipt")listOf(Triple(money,amount,0.0),Triple(other,0.0,amount))else listOf(Triple(other,amount,0.0),Triple(money,0.0,amount)))
 val db=writableDatabase
 val year=date.take(4).toInt()
 val number=db.rawQuery("SELECT COALESCE(MAX(voucher_number),0)+1 FROM vouchers WHERE voucher_type=? AND fiscal_year=?",arrayOf(type,"$year")).use{it.moveToFirst();it.getInt(0)}
 val voucherCode=(if(type=="receipt")"RV" else "PV")+"-$year-"+number.toString().padStart(4,'0')
 val id=read("SELECT id FROM journal_entries WHERE entry_code=?",arrayOf(code)).first().getValue("id").toLong()
 db.insertOrThrow("vouchers",null,ContentValues().apply{put("voucher_type",type);put("fiscal_year",year);put("voucher_number",number);put("voucher_code",voucherCode);put("voucher_date",date);put("method",if(money==1L)"cash" else "bank");put("money_account_id",money);put("counterparty_account_id",other);put("amount",amount);put("description",desc);put("journal_entry_id",id)})
 return voucherCode
 }
 fun trial()=read("SELECT a.code,a.name,ROUND(COALESCE(SUM(l.debit),0),3) debit,ROUND(COALESCE(SUM(l.credit),0),3) credit FROM accounts a LEFT JOIN journal_lines l ON a.id=l.account_id GROUP BY a.id ORDER BY a.code")
 fun journal()=read("SELECT e.entry_date,e.entry_code,e.description,a.code account_code,a.name account_name,l.debit,l.credit FROM journal_entries e JOIN journal_lines l ON e.id=l.journal_entry_id JOIN accounts a ON a.id=l.account_id ORDER BY e.entry_date DESC,e.entry_number DESC,l.id")
 fun dashboard()=read("SELECT COALESCE(SUM(CASE WHEN a.account_type='revenue' THEN l.credit-l.debit ELSE 0 END),0) revenue,COALESCE(SUM(CASE WHEN a.account_type='expense' THEN l.debit-l.credit ELSE 0 END),0) expense FROM journal_lines l JOIN accounts a ON a.id=l.account_id").first()
}
