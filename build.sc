import coursier.maven.MavenRepository
import mill._
import mill.scalalib._
import mill.scalalib.publish._

import java.text.SimpleDateFormat
import java.util.Date
import java.util.jar.Attributes.Name.{IMPLEMENTATION_VERSION => ImplementationVersion}
import scala.sys.process.Process

/** Base trait for build modules. */
trait CommonModule extends SbtModule {
  def deployLocal(assembly: PathRef, jarName: String): Unit = {
    os.makeDir.all(os.pwd / Symbol("jars"))
    println(s"Copying artifact ${assembly.path} to jars / $jarName")
    os.copy(assembly.path, os.pwd / Symbol("jars") / jarName, replaceExisting=true)
  }

  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/public"),
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
      MavenRepository("https://jcenter.bintray.com/"),
      MavenRepository("https://artifactory.broadinstitute.org/artifactory/libs-snapshot/")
    )
  }
}

/** A base trait for versioning modules. */
trait ReleaseModule extends JavaModule {
  /** Execute Git arguments and return the standard output. */
  private def git(args: String*): String = os.proc("git", args).call().out.text().trim

  /** Get the commit hash at the HEAD of this branch. */
  private def gitHead: String = git("rev-parse", "HEAD")

  /** Get the commit shorthash at the HEAD of this branch .*/
  private def shortHash: String = gitHead.take(7)

  /** If the Git repository is left in a dirty state. */
  private def dirty: Boolean = git("status", "--porcelain").nonEmpty

  private def today: String = new SimpleDateFormat("yyyyMMdd").format(new Date())

  /** The implementation version. */
  private def implementationVersion = T input {
    val prefix = s"$today-$shortHash"
    if (dirty) s"$prefix-dirty" else prefix
  }

  /** The version string `Target`. */
  def version = T input { println(implementationVersion()) }

  /** The JAR manifest. */
  override def manifest = T { super.manifest().add(ImplementationVersion.toString -> implementationVersion()) }
}


object tools extends CommonModule with PublishModule with ReleaseModule {
  def scalaVersion = "2.13.8"
  override def mainClass = Some("com.client.cmdline.ClientMain")
  override def artifactName = "client-tools"
  def gitHash = Process("git rev-parse --short HEAD").lazyLines.head
  def publishVersion = s"0.0.1-$gitHash-SNAPSHOT"
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.client",
    url = "https://github.com/fulcrumgenomics/client-tools",
    licenses = Seq(new License(id="Private", name="Private", url="n/a", isOsiApproved=false, isFsfLibre=false, distribution="no")),
    versionControl = VersionControl.github("fulcrumgenomics", "scala-mill-skeleton"),
    developers = Seq(
      Developer("tfenne", "Tim Fennell", "https://github.com/tfenne"),
      Developer("nh13", "Nils Homer", "https://github.com/nh13"),
    )
  )

  private val orgsToExclude = Seq(
    "org.apache.ant",
    "gov.nih.nlm.ncbi",
    "org.testng",
    "com.google.cloud.genomics"
  )

  override def ivyDeps = Agg(
    ivy"org.scala-lang:scala-compiler:${scalaVersion()}",
    ivy"com.fulcrumgenomics:commons_2.13:1.1.0",
    ivy"com.fulcrumgenomics:sopt_2.13:1.1.0",
    ivy"com.fulcrumgenomics:fgbio_2.13:1.4.0-61dde53-SNAPSHOT".excludeOrg(orgsToExclude:_*),
    )

  object test extends Tests {
    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.1.0")
    override def testFramework = "org.scalatest.tools.Framework"

    // run mill tools.test.singleTest com.client.x.y.x.TestClassName
    def singleTest(args: String*) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }
  }

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8")

  def deployLocal = T { super.deployLocal(assembly(), "client-tools.jar")  }
}
