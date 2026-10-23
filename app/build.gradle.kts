import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins{id("com.android.application");id("org.jetbrains.kotlin.android");id("org.jetbrains.kotlin.plugin.compose")}
android{
 namespace="dev.nerd.fm";compileSdk=34
 defaultConfig{applicationId="dev.nerd.fm";minSdk=24;targetSdk=34
  versionCode=(findProperty("vCode") as String?)?.toInt()?:1
  versionName=(findProperty("vName") as String?)?:"0.0.1"
  resourceConfigurations+=listOf("en")}
 signingConfigs{create("r"){storeFile=file(System.getenv("KS")?:"ks.jks");storePassword=System.getenv("KS_PASS");keyAlias="k";keyPassword=System.getenv("KS_PASS")}}
 buildTypes{release{isMinifyEnabled=true;isShrinkResources=true;signingConfig=signingConfigs["r"]
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")}}
 buildFeatures{compose=true}
 lint{checkReleaseBuilds=false;abortOnError=false}
 packaging{resources.excludes+=listOf("/META-INF/{AL2.0,LGPL2.1}","/META-INF/*.version","DebugProbesKt.bin","kotlin-tooling-metadata.json")}
}
kotlin{jvmToolchain(17)}
tasks.withType<KotlinCompile>().configureEach{compilerOptions.freeCompilerArgs.addAll("-Xno-param-assertions","-Xno-call-assertions","-Xno-receiver-assertions")}
dependencies{
 implementation(platform("androidx.compose:compose-bom:2024.09.00"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.9.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
 implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
