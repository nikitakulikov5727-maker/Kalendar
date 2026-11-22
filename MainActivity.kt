package ru.rabochiykalendar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Bg = Color(0xFF171717)
private val Panel = Color(0xFF252525)
private val Accent = Color(0xFF00CDBB)
private val Muted = Color(0xFF9E9E9E)
private val Red = Color(0xFFFF6577)
private val Blue = Color(0xFF5592FF)

data class WorkEntry(
    val id: Long = 0, val day: String, val title: String,
    val start: String, val end: String, val minutes: Int, val rate: Double
) {
    val amount: Double get() = minutes / 60.0 * rate
}
class Store(ctx: Context) : SQLiteOpenHelper(ctx, "work_calendar.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, title TEXT, start TEXT, end TEXT, minutes INTEGER, rate REAL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun all(): List<WorkEntry> {
        val out = mutableListOf<WorkEntry>()
        readableDatabase.rawQuery("SELECT id,day,title,start,end,minutes,rate FROM entries ORDER BY day", null).use { c ->
            while (c.moveToNext()) out += WorkEntry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getDouble(6))
        }; return out
    }
    fun add(e: WorkEntry) {
        val v=ContentValues().apply { put("day",e.day);put("title",e.title);put("start",e.start);put("end",e.end);put("minutes",e.minutes);put("rate",e.rate) }
        writableDatabase.insert("entries",null,v)
    }
    fun delete(id: Long) { writableDatabase.delete("entries","id=?", arrayOf(id.toString())) }
    fun deleteDay(day: String) { writableDatabase.delete("entries","day=?",arrayOf(day)) }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val store=Store(this); setContent { App(store) } }
}
@Composable fun App(store: Store) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var rows by remember { mutableStateOf(store.all()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var rangeMode by remember { mutableStateOf(false) }
    var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }
    var statsOpen by remember { mutableStateOf(true) }
    var showEditor by remember { mutableStateOf(false) }
    fun refresh() { rows=store.all() }
    val periodRows = rows.filter {
        val d=LocalDate.parse(it.day)
        if (rangeMode && rangeStart != null && rangeEnd != null) !d.isBefore(minOf(rangeStart!!,rangeEnd!!)) && !d.isAfter(maxOf(rangeStart!!,rangeEnd!!))
        else YearMonth.from(d)==month
    }
    val hours=periodRows.sumOf{it.minutes}/60.0
    val income=periodRows.sumOf{it.amount}
    MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel)) {
        Surface(Modifier.fillMaxSize(), color=Bg) {
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Text("Рабочий календарь",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={rangeMode=!rangeMode;rangeStart=null;rangeEnd=null}) { Text(if(rangeMode) "Месяц" else "Период",color=Accent) }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={month=month.minusMonths(1)}) { Icon(Icons.Default.ChevronLeft,null,tint=Color.White) }
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.uppercase()},fontSize=20.sp,fontWeight=FontWeight.Bold)
                    IconButton(onClick={month=month.plusMonths(1)}) { Icon(Icons.Default.ChevronRight,null,tint=Color.White) }
                }
                if(rangeMode) {
                    Text("Выбери начало и конец периода нажатием на даты в календаре",color=Muted,fontSize=12.sp)
                    Text("Начало: ${rangeStart ?: "—"}     Конец: ${rangeEnd ?: "—"}",color=Accent)
                }
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС").forEach { Text(it,Modifier.weight(1f),color=Muted,fontSize=12.sp) }
                }
                val first=month.atDay(1)
                val offset=first.dayOfWeek.value-1
                val cells=((offset+month.lengthOfMonth()+6)/7)*7
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    for (week in 0 until cells/7) Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        for (col in 0..6) {
                            val n=week*7+col-offset+1
                            val date=if(n in 1..month.lengthOfMonth()) month.atDay(n) else null
                            val dayRows=if(date==null) emptyList() else rows.filter{it.day==date.toString()}
                            val mins=dayRows.sumOf{it.minutes}
                            val pay=dayRows.sumOf{it.amount}
                            val chosen=date!=null && (selected==date || (rangeMode && (rangeStart==date || rangeEnd==date)))
                            Column(Modifier.weight(1f).height(83.dp).background(if(chosen) Color(0xFF34413F) else Panel,RoundedCornerShape(5.dp))
                                .clickable(enabled=date!=null) {
                                    if(rangeMode) { if(rangeStart==null || (rangeStart!=null && rangeEnd!=null)) {rangeStart=date;rangeEnd=null} else rangeEnd=date }
                                    else selected=date
                                }.padding(5.dp),verticalArrangement=Arrangement.SpaceBetween) {
                                Text(date?.dayOfMonth?.toString() ?: "",color=if(date==null) Color.DarkGray else Color.White,fontSize=12.sp)
                                if(mins>0) {
                                    Text("${mins/60}ч ${mins%60}м",color=if(dayRows.size>1) Blue else Red,fontSize=12.sp,fontWeight=FontWeight.Bold)
                                    Text(pay.toMoney(),color=Color(0xFF49C94D),fontSize=10.sp,maxLines=1)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(rangeMode) "ИТОГ ЗА ПЕРИОД" else "ИТОГ ЗА МЕСЯЦ",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={statsOpen=!statsOpen}) { Text(if(statsOpen) "Скрыть" else "Показать",color=Accent) }
                }
                if(statsOpen) Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Stat("Дней отработано",periodRows.map{it.day}.distinct().size.toString())
                        Stat("Часов за период","%d:%02d".format((hours*60).toInt()/60,(hours*60).toInt()%60))
                        Stat("Доход за период",income.toMoney()+" ₽")
                        if(!rangeMode) {
                            val yearIncome = rows.filter { LocalDate.parse(it.day).year == month.year }.sumOf { it.amount }
                            Stat("Доход за ${month.year} год",yearIncome.toMoney()+" ₽")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if(selected!=null && !rangeMode) {
                    val date=selected!!
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ru"))),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Button(onClick={selected=null}) { Text("Закрыть") }
                    }
                    val entries=rows.filter{it.day==date.toString()}
                    entries.forEach { e -> Row(Modifier.fillMaxWidth().padding(vertical=3.dp).background(Panel,RoundedCornerShape(8.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${e.title} • ${e.start}–${e.end}",fontSize=13.sp);Text("${e.minutes/60}ч ${e.minutes%60}м × ${e.rate.toMoney()} ₽ = ${e.amount.toMoney()} ₽",color=Muted,fontSize=12.sp) }
                        IconButton(onClick={store.delete(e.id);refresh()}) { Icon(Icons.Default.Delete,null,tint=Red) }
                    } }
                    Button(onClick={showEditor=true},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Add,null);Text("Добавить тариф / смену") }
                    Text("Доход за день: ${entries.sumOf{it.amount}.toMoney()} ₽",color=Accent,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
    if(showEditor && selected!=null) EntryDialog(selected!!, onDismiss={showEditor=false}, onSave={entry -> store.add(entry);refresh();showEditor=false})
}
@Composable fun Stat(label:String,value:String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,color=Muted);Text(value,fontWeight=FontWeight.SemiBold) } }
@Composable fun EntryDialog(date:LocalDate,onDismiss:()->Unit,onSave:(WorkEntry)->Unit) {
    var title by remember { mutableStateOf("Основной тариф") }
    var start by remember { mutableStateOf("22:15") }
    var end by remember { mutableStateOf("06:00") }
    var rate by remember { mutableStateOf("530") }
    var manual by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("7") }
    var minutes by remember { mutableStateOf("45") }
    val duration=if(manual) (hours.toIntOrNull()?:0)*60+(minutes.toIntOrNull()?:0) else {
        val a=parseTime(start);val b=parseTime(end)
        if(a==null||b==null) 0 else if(b>=a) b-a else b+1440-a
    }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text("Смена • $date")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Название тарифа")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(start,{start=it},modifier=Modifier.weight(1f),label={Text("Начало ЧЧ:ММ")},singleLine=true)
                OutlinedTextField(end,{end=it},modifier=Modifier.weight(1f),label={Text("Конец ЧЧ:ММ")},singleLine=true)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(manual,{manual=it});Text("Ввести длительность вручную")
            }
            if(manual) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(hours,{hours=it},modifier=Modifier.weight(1f),label={Text("Часы")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                OutlinedTextField(minutes,{minutes=it},modifier=Modifier.weight(1f),label={Text("Минуты")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }
            OutlinedTextField(rate,{rate=it},label={Text("Ставка ₽/час")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            Text("Продолжительность: ${duration/60} ч ${duration%60} мин",color=Accent)
            Text("Начало 22:15 и окончание 06:00 автоматически дадут 7 ч 45 мин.",fontSize=11.sp,color=Muted)
        }
    },confirmButton={TextButton(onClick={
        val r=rate.replace(',','.').toDoubleOrNull()?:0.0
        if(duration>0 && r>=0) onSave(WorkEntry(day=date.toString(),title=title,start=start,end=end,minutes=duration,rate=r))
    }) { Text("Сохранить") }},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
fun parseTime(s:String):Int? { val p=s.split(":");if(p.size!=2)return null;val h=p[0].toIntOrNull()?:return null;val m=p[1].toIntOrNull()?:return null;return if(h in 0..23&&m in 0..59)h*60+m else null }
fun Double.toMoney()=String.format(Locale("ru","RU"),"%.2f",this)
