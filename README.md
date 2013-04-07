# clj-gcon

Genome Connector is a generic API providing access to multiple resources and
software for biological analysis. It improves interoperability of tools by
providing an abstraction layer for file access, organization and metadata
retrieval from multiple locations. Currently it aims to handle:

- Authentication
- Queries of files grouped into collections (via folders)
- Retrieval of files stored at remote tools
- Push to files stored at remote tools
- Reading and writing of metadata on files and collections

It wraps:

- [GenomeSpace][1], using [clj-genomespace][0]
- [Galaxy][3], using [clj-blend][2]

and also aims to support:

- [BaseSpace][4]
- AmazonS3 and other key value file stores

[0]: https://github.com/chapmanb/clj-genomespace
[1]: http://www.genomespace.org/
[2]: https://github.com/chapmanb/clj-blend
[3]: http://usegalaxy.org
[4]: https://developer.basespace.illumina.com/

## Usage

## License

The code is freely available under the [MIT license][l1].

[l1]: http://www.opensource.org/licenses/mit-license.html
